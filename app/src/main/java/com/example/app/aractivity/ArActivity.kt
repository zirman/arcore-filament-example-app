package com.example.app.aractivity

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import com.example.app.*
import com.example.app.arcore.ArCore
import com.example.app.databinding.ExampleActivityBinding
import com.example.app.filament.Filament
import com.example.app.gesture.*
import com.example.app.toRadians
import com.example.app.x
import com.example.app.y
import com.example.app.renderer.*
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ArActivity : AppCompatActivity() {
    private val resumeBehavior: MutableStateFlow<Unit?> =
        MutableStateFlow(null)

    private val requestPermissionResultEvents: MutableSharedFlow<PermissionResultEvent> =
        MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val configurationChangedEvents: MutableSharedFlow<Configuration> =
        MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val dragEvents: MutableSharedFlow<Pair<ViewRect, TouchEvent>> =
        MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val scaleEvents: MutableSharedFlow<Float> =
        MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val rotateEvents: MutableSharedFlow<Float> =
        MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val arTrackingEvents: MutableSharedFlow<Unit> =
        MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val arCoreBehavior: MutableStateFlow<Pair<ArCore, FrameCallback>?> =
        MutableStateFlow(null)

    private lateinit var transformationSystem: TransformationSystem

    private val createScope = CoroutineScope(Dispatchers.Main)
    private lateinit var startScope: CoroutineScope
    private lateinit var binding: ExampleActivityBinding

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ExampleActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            findViewById<View>(android.R.id.content)!!.windowInsetsController!!
                .also { windowInsetsController ->
                    windowInsetsController.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                    windowInsetsController.hide(WindowInsets.Type.systemBars())
                }
        } else @Suppress("DEPRECATION") run {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility
                .or(View.SYSTEM_UI_FLAG_FULLSCREEN)       // hide status bar
                .or(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)  // hide navigation bar
                .or(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) // hide stat/nav bar after interaction timeout
        }

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
                        },
                    )
                }

                fun update(gesture: PinchGesture) {
                    scaleEvents.tryEmit(1f + gesture.gapDeltaInches())
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
                        },
                    )
                }

                fun update(gesture: TwistGesture) {
                    rotateEvents.tryEmit(-gesture.deltaRotationDegrees.toRadians)
                }
            }
        )

        // drag gesture events
        transformationSystem.dragRecognizer.addOnGestureStartedListener(
            object : DragGestureRecognizer.OnGestureStartedListener {
                override fun onGestureStarted(gesture: DragGesture) {
                    Pair(
                        binding.surfaceView.toViewRect(),
                        TouchEvent.Move(gesture.position.x, gesture.position.y),
                    )
                        .let { dragEvents.tryEmit(it) }

                    gesture.setGestureEventListener(
                        object : DragGesture.OnGestureEventListener {
                            override fun onFinished(gesture: DragGesture) {
                                Pair(
                                    binding.surfaceView.toViewRect(),
                                    TouchEvent.Stop(gesture.position.x, gesture.position.y),
                                )
                                    .let { dragEvents.tryEmit(it) }
                            }

                            override fun onUpdated(gesture: DragGesture) {
                                Pair(
                                    binding.surfaceView.toViewRect(),
                                    TouchEvent.Move(gesture.position.x, gesture.position.y),
                                )
                                    .let { dragEvents.tryEmit(it) }
                            }
                        },
                    )
                }
            },
        )

        // tap and gesture events
        binding.surfaceView.setOnTouchListener { _, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_UP &&
                (motionEvent.eventTime - motionEvent.downTime) <
                resources.getInteger(R.integer.tap_event_milliseconds)
            ) {
                Pair(
                    binding.surfaceView.toViewRect(),
                    TouchEvent.Stop(motionEvent.x, motionEvent.y),
                )
                    .let { dragEvents.tryEmit(it) }
            }

            transformationSystem.onTouch(motionEvent)
            true
        }

        createScope.launch {
            try {
                createUx()
            } catch (error: Throwable) {
                if (error !is UserCanceled) {
                    error.printStackTrace()
                }
            } finally {
                finish()
            }
        }
    }

    override fun onDestroy() {
        createScope.cancel()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        startScope = CoroutineScope(Dispatchers.Main)

        startScope.launch {
            try {
                startUx()
            } catch (error: Throwable) {
                if (error !is UserCanceled) {
                    error.printStackTrace()
                }

                finish()
            }
        }
    }

    override fun onStop() {
        startScope.cancel()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        resumeBehavior.tryEmit(Unit)
    }

    override fun onPause() {
        super.onPause()
        resumeBehavior.tryEmit(null)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configurationChangedEvents.tryEmit(newConfig)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        requestPermissionResultEvents.tryEmit(PermissionResultEvent(requestCode, grantResults))
    }


    private suspend fun createUx() {
        // wait for activity to resume
        resumeBehavior.filterNotNull().first()

        if (checkIfOpenGlVersionSupported(minOpenGlVersion).not()) {
            showOpenGlNotSupportedDialog(this@ArActivity)
            // finish()
            throw OpenGLVersionNotSupported
        }

        resumeBehavior.filterNotNull().first()

        // if arcore is not installed, request to install
        if (ArCoreApk
                .getInstance()
                .requestInstall(
                    this@ArActivity,
                    true,
                    ArCoreApk.InstallBehavior.REQUIRED,
                    ArCoreApk.UserMessageType.USER_ALREADY_INFORMED,
                ) == ArCoreApk.InstallStatus.INSTALL_REQUESTED
        ) {
            // make sure activity is paused before waiting for resume
            resumeBehavior.dropWhile { it != null }.filterNotNull().first()

            // check if install succeeded
            if (ArCoreApk
                    .getInstance()
                    .requestInstall(
                        this@ArActivity,
                        false,
                        ArCoreApk.InstallBehavior.REQUIRED,
                        ArCoreApk.UserMessageType.USER_ALREADY_INFORMED,
                    ) != ArCoreApk.InstallStatus.INSTALLED
            ) {
                throw UserCanceled
            }
        }

        // if permission is not granted, request permission
        if (ContextCompat.checkSelfPermission(
                this@ArActivity,
                Manifest.permission.CAMERA,
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            showCameraPermissionDialog(this@ArActivity)

            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                cameraPermissionRequestCode,
            )

            // check if permission was granted
            if (requestPermissionResultEvents
                    .filter { it.requestCode == cameraPermissionRequestCode }
                    .first()
                    .grantResults.any { it != PackageManager.PERMISSION_GRANTED }
            ) {
                throw UserCanceled
            }
        }

        // TODO: eliminate nesting of finally blocks
        val filament = Filament(this@ArActivity, binding.surfaceView)

        try {
            val arCore = ArCore(this@ArActivity, filament, binding.surfaceView)

            try {
                val lightRenderer = LightRenderer(this@ArActivity, arCore.filament)
                val planeRenderer = PlaneRenderer(this@ArActivity, arCore.filament)
                val modelRenderer = ModelRenderer(this@ArActivity, arCore, arCore.filament)

                try {
                    val frameCallback =
                        FrameCallback(
                            arCore,
                            doFrame = { frame ->
                                if (frame.getUpdatedTrackables(Plane::class.java)
                                        .any { it.trackingState == TrackingState.TRACKING }
                                ) {
                                    arTrackingEvents.tryEmit(Unit)
                                }

                                lightRenderer.doFrame(frame)
                                planeRenderer.doFrame(frame)
                                modelRenderer.doFrame(frame)
                            },
                        )

                    arCoreBehavior.emit(Pair(arCore, frameCallback))

                    with(CoroutineScope(coroutineContext)) {
                        launch {
                            configurationChangedEvents.collect { arCore.configurationChange() }
                        }

                        launch {
                            dragEvents
                                .map { (viewRect, touchEvent) ->
                                    ScreenPosition(
                                        x = touchEvent.x / viewRect.width,
                                        y = touchEvent.y / viewRect.height,
                                    )
                                        .let { ModelRenderer.ModelEvent.Move(it) }
                                }
                                .collect { modelRenderer.modelEvents.tryEmit(it) }
                        }

                        launch {
                            scaleEvents
                                .map { ModelRenderer.ModelEvent.Update(0f, it) }
                                .collect { modelRenderer.modelEvents.tryEmit(it) }
                        }

                        launch {
                            rotateEvents
                                .map { ModelRenderer.ModelEvent.Update(it, 1f) }
                                .collect { modelRenderer.modelEvents.tryEmit(it) }
                        }
                    }

                    awaitCancellation()
                } finally {
                    modelRenderer.destroy()
                }
            } finally {
                arCore.destroy()
            }
        } finally {
            filament.destroy()
        }
    }

    private suspend fun startUx() {
        val (arCore, frameCallback) = arCoreBehavior.filterNotNull().first()

        try {
            arCore.session.resume()
            frameCallback.start()

            coroutineScope {
                val job = launch(coroutineContext) {
                    TimeUnit.SECONDS
                        .toMillis(
                            resources
                                .getInteger(R.integer.show_hand_motion_timeout_seconds)
                                .toLong(),
                        )
                        .let { delay(it) }

                    binding.handMotionContainer.isVisible = true
                }

                launch(coroutineContext) {
                    arTrackingEvents.first()
                    job.cancel()
                    binding.handMotionContainer.isVisible = false
                }
            }

            awaitCancellation()
        } finally {
            binding.handMotionContainer.isVisible = false
            frameCallback.stop()
            arCore.session.pause()
        }
    }

    private suspend fun showCameraPermissionDialog(activity: AppCompatActivity) {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val alertDialog = AlertDialog
                    .Builder(activity)
                    .setTitle(R.string.camera_permission_title)
                    .setMessage(R.string.camera_permission_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        continuation.resume(Unit)
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        continuation.resumeWithException(UserCanceled)
                    }
                    .setCancelable(false)
                    .show()

                continuation.invokeOnCancellation { alertDialog.dismiss() }
            }
        }
    }
}
