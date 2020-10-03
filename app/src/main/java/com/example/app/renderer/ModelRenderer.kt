package com.example.app.renderer

import android.content.Context
import com.example.app.*
import com.example.app.aractivity.ScreenPosition
import com.example.app.arcore.ArCore
import com.example.app.filament.Filament
import com.google.android.filament.gltfio.FilamentAsset
import com.google.ar.core.Frame
import com.google.ar.core.Point
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class ModelRenderer(context: Context, private val arCore: ArCore, private val filament: Filament) {
    sealed class ModelEvent {
        data class Move(val screenPosition: ScreenPosition) : ModelEvent()
        data class Update(val rotate: Float, val scale: Float) : ModelEvent()
    }

    val modelEvents: PublishSubject<ModelEvent> = PublishSubject.create()

    private val doFrameEvent: PublishSubject<Frame> = PublishSubject.create()
    private val compositeDisposable = CompositeDisposable()
    private var translationBehavior: BehaviorSubject<V3> = BehaviorSubject.create()
    private var rotateScaleBehavior: BehaviorSubject<Pair<Float, Float>> = BehaviorSubject.create()

    init {
        // translation
        modelEvents
            .flatMap { modelEvent ->
                (modelEvent as? ModelEvent.Move)
                    ?.let {
                        arCore.frame
                            .hitTest(
                                filament.surfaceView.width.toFloat() * modelEvent.screenPosition.x,
                                filament.surfaceView.height.toFloat() * modelEvent.screenPosition.y
                            )
                            .maxByOrNull { it.trackable is Point }
                    }
                    ?.let { V3(it.hitPose.translation) }
                    ?.let { Observable.just(it) }
                    ?: Observable.empty()
            }
            .subscribe({ translationBehavior.onNext(it) }, {})
            .also { compositeDisposable.add(it) }

        // rotation and scale
        modelEvents
            .scan(Pair(0f, 1f), { (rotate, scale), modelEvent ->
                when (modelEvent) {
                    is ModelEvent.Update ->
                        Pair((rotate + modelEvent.rotate).clampToTau, scale * modelEvent.scale)
                    else ->
                        Pair(rotate, scale)
                }
            })
            .subscribe({ rotateScaleBehavior.onNext(it) }, {})
            .also { compositeDisposable.add(it) }

        // update filament
        Single
            .create<FilamentAsset> { singleEmitter ->
                context.assets
                    .open("eren-hiphop-dance.glb")
                    .use { input ->
                        val bytes = ByteArray(input.available())
                        input.read(bytes)

                        filament.assetLoader
                            .createAssetFromBinary(ByteBuffer.wrap(bytes))!!
                            .let { singleEmitter.onSuccess(it) }
                    }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess { filament.resourceLoader.loadResources(it) }
            .flatMapObservable { filamentAsset ->
                Observable.merge(
                    doFrameEvent.map { frame ->
                        // update animator
                        val animator = filamentAsset.animator

                        if (animator.animationCount > 0) {
                            animator.applyAnimation(
                                0,
                                (frame.timestamp /
                                        TimeUnit.SECONDS.toNanos(1).toDouble())
                                    .toFloat() %
                                        animator.getAnimationDuration(0)
                            )

                            animator.updateBoneMatrices()
                        }
                    },
                    Observable.combineLatest(
                        translationBehavior,
                        rotateScaleBehavior,
                        doFrameEvent,
                        { translation, (rotation, scale), _ ->
                            filament.scene.addEntities(filamentAsset.entities)

                            filament.engine.transformManager.setTransform(
                                filament.engine.transformManager.getInstance(filamentAsset.root),
                                m4Identity()
                                    .translate(translation.x, translation.y, translation.z)
                                    .rotate(rotation.toDegrees, 0f, 1f, 0f)
                                    .scale(scale, scale, scale)
                                    .floatArray,
                            )
                        }
                    )
                )
            }
            .subscribe({}, {})
            .also { compositeDisposable.add(it) }
    }

    fun destroy() {
        compositeDisposable.dispose()
    }

    fun doFrame(frame: Frame) {
        doFrameEvent.onNext(frame)
    }
}
