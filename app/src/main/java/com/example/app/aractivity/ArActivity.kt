package com.example.app.aractivity

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.app.*
import com.example.app.arcore.ArCore
import com.example.app.arcore.checkArCore
import com.example.app.filament.Filament
import com.example.app.gesture.*
import com.example.app.toRadians
import com.example.app.x
import com.example.app.y
import com.example.app.renderer.*
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit

class ArActivity : AppCompatActivity(), AutoHideSystemUi, RequestPermissionResultEvents,
    ConfigurationChangedEvents, ResumeBehavior {

    override val onSystemUiFlagHideNavigationDisposable: CompositeDisposable =
        CompositeDisposable()

    override val resumeBehavior: BehaviorSubject<Boolean> =
        BehaviorSubject.create()

    override val requestPermissionResultEvents: PublishSubject<PermissionResultEvent> =
        PublishSubject.create()

    override val configurationChangedEvents: PublishSubject<Configuration> =
        PublishSubject.create()

    private val arStateSignal: BehaviorSubject<ArState> =
        BehaviorSubject.create()

    private val dragEvents: PublishSubject<Pair<ViewRect, TouchEvent>> =
        PublishSubject.create()

    private val scaleEvents: PublishSubject<Float> =
        PublishSubject.create()

    private val rotateEvents: PublishSubject<Float> =
        PublishSubject.create()

    private val grantedPermissionsAndArCoreInstalledSignal: BehaviorSubject<Unit> =
        BehaviorSubject.create()

    private val arTrackingEvents: PublishSubject<Unit> =
        PublishSubject.create()

    private val onCreateDisposable: CompositeDisposable =
        CompositeDisposable()

    private val onStartDisposable: CompositeDisposable =
        CompositeDisposable()

    private lateinit var textureView: TextureView
    private lateinit var handMotionContainer: FrameLayout
    private lateinit var transformationSystem: TransformationSystem

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onCreateAutoHideSystemUi()

        setContentView(R.layout.example_activity)
        textureView = findViewById(R.id.texture_view)!!
        handMotionContainer = findViewById(R.id.hand_motion_container)!!

        transformationSystem = TransformationSystem(resources.displayMetrics)

        // pinch gesture events
        transformationSystem.pinchRecognizer.addOnGestureStartedListener(
            object : PinchGestureRecognizer.OnGestureStartedListener {
                override fun onGestureStarted(gesture: PinchGesture) {
                    update(gesture)

                    gesture.setGestureEventListener(
                        object : PinchGesture.OnGestureEventListener {
                            override fun onFinished(gesture: PinchGesture) {
                                update(gesture)
                            }

                            override fun onUpdated(gesture: PinchGesture) {
                                update(gesture)
                            }
                        }
                    )
                }

                fun update(gesture: PinchGesture) {
                    scaleEvents.onNext(1f + gesture.gapDeltaInches())
                }
            }
        )

        // twist gesture events
        transformationSystem.twistRecognizer.addOnGestureStartedListener(
            object : TwistGestureRecognizer.OnGestureStartedListener {
                override fun onGestureStarted(gesture: TwistGesture) {
                    update(gesture)

                    gesture.setGestureEventListener(
                        object : TwistGesture.OnGestureEventListener {
                            override fun onFinished(gesture: TwistGesture) {
                                update(gesture)
                            }

                            override fun onUpdated(gesture: TwistGesture) {
                                update(gesture)
                            }
                        }
                    )
                }

                fun update(gesture: TwistGesture) {
                    rotateEvents.onNext(-gesture.deltaRotationDegrees.toRadians)
                }
            }
        )

        // drag gesture events
        transformationSystem.dragRecognizer.addOnGestureStartedListener(
            object : DragGestureRecognizer.OnGestureStartedListener {
                override fun onGestureStarted(gesture: DragGesture) {
                    Pair(
                        textureView.toViewRect(),
                        TouchEvent.Move(gesture.position.x, gesture.position.y)
                    )
                        .let { dragEvents.onNext(it) }

                    gesture.setGestureEventListener(
                        object : DragGesture.OnGestureEventListener {
                            override fun onFinished(gesture: DragGesture) {
                                Pair(
                                    textureView.toViewRect(),
                                    TouchEvent.Stop(gesture.position.x, gesture.position.y)
                                )
                                    .let { dragEvents.onNext(it) }
                            }

                            override fun onUpdated(gesture: DragGesture) {
                                Pair(
                                    textureView.toViewRect(),
                                    TouchEvent.Move(gesture.position.x, gesture.position.y)
                                )
                                    .let { dragEvents.onNext(it) }
                            }
                        }
                    )
                }
            }
        )

        // tap and gesture events
        textureView.setOnTouchListener { _, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_UP &&
                (motionEvent.eventTime - motionEvent.downTime) <
                resources.getInteger(R.integer.tap_event_milliseconds)
            ) {
                Pair(textureView.toViewRect(), TouchEvent.Stop(motionEvent.x, motionEvent.y))
                    .let { dragEvents.onNext(it) }
            }

            transformationSystem.onTouch(motionEvent)
            true
        }

        resumeBehavior
            .filter { it }
            .firstOrError()
            .flatMap { checkPermissions() }
            .flatMap { checkArCore() }
            .subscribe(
                { grantedPermissionsAndArCoreInstalledSignal.onNext(Unit) },
                { errorHandler(it) }
            )
            .let { onCreateDisposable.add(it) }

        grantedPermissionsAndArCoreInstalledSignal
            .firstOrError()
            .flatMapObservable { ArCore.arCoreSignal(this, textureView) }
            .flatMap { arCore ->
                Observable.create<ArState> { observableEmitter ->
                    val filament = Filament(this, arCore, textureView)

                    val cameraRenderer = CameraRenderer(filament, arCore)
                    val lightRenderer = LightRenderer(filament)
                    val planeRenderer = PlaneRenderer(this, filament)
                    val cursorRenderer = ModelRenderer(this, arCore, filament)

                    val frameCallback = FrameCallback(
                        arCore,
                        filament,
                        doFrame = { frame ->
                            if (frame.getUpdatedTrackables(Plane::class.java)
                                    .any { it.trackingState == TrackingState.TRACKING }
                            ) {
                                arTrackingEvents.onNext(Unit)
                            }

                            cameraRenderer.doFrame(frame)
                            lightRenderer.doFrame(frame)
                            planeRenderer.doFrame(frame)
                            cursorRenderer.doFrame(frame)
                        }
                    )

                    ArState(
                        arCore,
                        filament,
                        cameraRenderer,
                        lightRenderer,
                        planeRenderer,
                        cursorRenderer,
                        frameCallback
                    )
                        .let { observableEmitter.onNext(it) }

                    observableEmitter.setCancellable {
                        cursorRenderer.destroy()
                        filament.destroy()
                    }
                }
                    .subscribeOn(AndroidSchedulers.mainThread())
            }
            .subscribe({ arStateSignal.onNext(it) }, { errorHandler(it) })
            .let { onCreateDisposable.add(it) }

        arStateSignal
            .firstOrError()
            .flatMapObservable { arState ->
                configurationChangedEvents.map { Pair(arState.arCore, arState.filament) }
            }
            .subscribe(
                { (arCore, filament) -> arCore.configurationChange(filament) },
                { errorHandler(it) }
            )
            .let { onCreateDisposable.add(it) }

        arStateSignal
            .firstOrError()
            .flatMapObservable { arState ->
                dragEvents.doOnNext { (viewRect, touchEvent) ->
                    ScreenPosition(
                        x = touchEvent.x / viewRect.width,
                        y = touchEvent.y / viewRect.height
                    )
                        .let { ModelRenderer.ModelEvent.Pointer(it) }
                        .let { arState.pointerRenderer.modelEvents.onNext(it) }
                }
            }
            .subscribe({}, { errorHandler(it) })
            .let { onCreateDisposable.add(it) }

        arStateSignal
            .firstOrError()
            .flatMapObservable { arState ->
                scaleEvents.doOnNext { scale ->
                    ModelRenderer.ModelEvent.Update(0f, scale)
                        .let { arState.pointerRenderer.modelEvents.onNext(it) }
                }
            }
            .subscribe({}, { errorHandler(it) })
            .let { onCreateDisposable.add(it) }

        arStateSignal
            .firstOrError()
            .flatMapObservable { arState ->
                rotateEvents.doOnNext { rotate ->
                    ModelRenderer.ModelEvent.Update(rotate, 1f)
                        .let { arState.pointerRenderer.modelEvents.onNext(it) }
                }
            }
            .subscribe({}, { errorHandler(it) })
            .let { onCreateDisposable.add(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        onCreateDisposable.clear()
    }

    override fun onStart() {
        super.onStart()

        arStateSignal
            .flatMap { arState ->
                Observable
                    .create<Nothing> { observableEmitter ->
                        arState.arCore.session.resume()
                        arState.frameCallback.start()

                        observableEmitter.setCancellable {
                            arState.frameCallback.stop()
                            arState.arCore.session.pause()
                        }
                    }
            }
            .subscribe({}, { errorHandler(it) })
            .let { onStartDisposable.add(it) }

        // visibility of handMotionContainer
        // show hand motion after resuming ar if nothing is tracked for 2 seconds
        Observable
            .amb(listOf(
                Observable
                    .concat(
                        Observable
                            .just(true)
                            .delay(
                                resources
                                    .getInteger(R.integer.show_hand_motion_timeout_seconds)
                                    .toLong(),
                                TimeUnit.SECONDS
                            ),
                        arTrackingEvents
                            .take(1)
                            .map { false }
                    ),
                arTrackingEvents
                    .take(1)
                    .map { false }
            ))
            .observeOn(AndroidSchedulers.mainThread())
            .doOnDispose { handMotionContainer.isVisible = false }
            .subscribe({ handMotionContainer.isVisible = it }, { errorHandler(it) })
            .let { onStartDisposable.add(it) }
    }

    override fun onStop() {
        super.onStop()
        onSystemUiFlagHideNavigationDisposable.clear()
        onStartDisposable.clear()
    }

    override fun onResume() {
        super.onResume()
        onResumeAutoHideSystemUi()
        resumeBehavior.onNext(true)
    }

    override fun onPause() {
        super.onPause()
        resumeBehavior.onNext(false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configurationChangedEvents.onNext(newConfig)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        requestPermissionResultEvents.onNext(PermissionResultEvent(requestCode, grantResults))
    }

    private fun checkPermissions(): Single<Unit> = Single
        .just(Unit)
        .flatMap {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                Single.just(Unit)
            } else {
                showCameraPermissionDialog(this)
                    .flatMap { getPermission() }
            }
        }

    private fun getPermission(): Single<Unit> = Single
        .just(Unit)
        .flatMap {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                cameraPermissionRequestCode
            )

            requestPermissionResultEvents
                .filter { it.requestCode == cameraPermissionRequestCode }
                .firstOrError()
        }
        .flatMap { permissionResult ->
            if (permissionResult.grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Single.just(Unit)
            } else {
                Single.error(UserCanceled())
            }
        }

    private fun showCameraPermissionDialog(activity: AppCompatActivity): Single<Unit> = Single
        .create { singleEmitter ->
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                val alertDialog = AlertDialog
                    .Builder(activity)
                    .setTitle(R.string.camera_permission_title)
                    .setMessage(R.string.camera_permission_message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> singleEmitter.onSuccess(Unit) }
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        singleEmitter.onError(UserCanceled())
                    }
                    .setCancelable(false)
                    .show()

                singleEmitter.setCancellable { alertDialog.dismiss() }
            } else {
                singleEmitter.onSuccess(Unit)
            }
        }

    private fun errorHandler(error: Throwable) {
        finish()
        if (error is UserCanceled) return
        error.printStackTrace()
    }
}
