package com.dsh.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.dsh.mobile.databinding.ActivityScanBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/**
 * 轻量二维码扫码页：CameraX 预览 + ZXing 纯 Java 解码。
 * 成功识别后将二维码文本通过 result 返回。
 */
class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private var camera: Camera? = null
    private var torchOn = false
    private var hasDecoded = false

    @Volatile
    private var lastDecodeAt = 0L

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(this, R.string.scan_permission_denied, Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 进入动效：从底部滑入 + 淡入
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.slide_in_bottom, R.anim.fade_out)

        binding.btnClose.setOnClickListener {
            Haptics.light(this)
            finish()
        }

        binding.btnTorch.setOnClickListener {
            Haptics.light(this)
            torchOn = !torchOn
            camera?.cameraControl?.enableTorch(torchOn)
            binding.btnTorch.alpha = if (torchOn) 0.7f else 1f
        }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    // 退出动效：淡入背景 + 滑出到底部
    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_bottom)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(analysisExecutor, ::analyzeFrame)
                }

            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, selector, preview, analysis)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.toast_no_camera, Toast.LENGTH_LONG).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** 将 CameraX 的 YUV 帧交给 ZXing 解码（在后台线程执行）。 */
    private fun analyzeFrame(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            // 节流：降低 CPU 占用
            if (now - lastDecodeAt < 350L) return

            val plane = image.planes[0]
            val buffer = plane.buffer
            val fullW = image.width
            val fullH = image.height
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride

            val data = ByteArray(buffer.remaining())
            buffer.get(data)

            // 将可能带行填充（rowStride > width）的 Y 平面重排为紧凑数组
            val luminance: ByteArray
            if (pixelStride == 1 && rowStride == fullW) {
                luminance = data
            } else {
                luminance = ByteArray(fullW * fullH)
                for (row in 0 until fullH) {
                    val srcPos = row * rowStride
                    val dstPos = row * fullW
                    if (srcPos + fullW <= data.size) {
                        System.arraycopy(data, srcPos, luminance, dstPos, fullW)
                    }
                }
            }

            val source = PlanarYUVLuminanceSource(luminance, fullW, fullH, 0, 0, fullW, fullH, false)
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decode(bitmap)
            val text = result.text
            if (!hasDecoded && !text.isNullOrBlank()) {
                lastDecodeAt = now
                hasDecoded = true
                runOnUiThread { deliver(text) }
            }
        } catch (_: NotFoundException) {
            // 未识别到二维码
        } catch (_: Exception) {
            // 忽略单帧异常，继续下一帧
        } finally {
            image.close()
        }
    }

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
            )
        )
    }

    private fun deliver(text: String) {
        val result = Intent().putExtra(EXTRA_QR, text)
        setResult(RESULT_OK, result)
        // 识别成功的触感确认，稍作停顿再返回
        Haptics.success(this)
        binding.root.postDelayed({ finish() }, 140)
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdown()
    }

    companion object {
        const val EXTRA_QR = "dsh_qr_text"
    }
}
