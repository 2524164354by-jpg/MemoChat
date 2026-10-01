package com.memochat.app.ui.crop

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.memochat.app.R
import java.io.File
import java.io.FileOutputStream

class CropActivity : AppCompatActivity() {

    private lateinit var image: ImageView
    private lateinit var frameView: CropFrameView
    private var bitmap: Bitmap? = null
    private val matrix = Matrix()
    private lateinit var scaleDetector: ScaleGestureDetector
    private var mode = 0
    private var lastX = 0f
    private var lastY = 0f

    companion object {
        const val EXTRA_URI = "uri"
        const val EXTRA_OUT = "out"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)
        try {
            image = findViewById(R.id.crop_image)
            frameView = findViewById(R.id.crop_frame)

            val uri: Uri? = intent.getParcelableExtra(EXTRA_URI)
            if (uri == null) { finish(); return }

            // decode bounds first
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            val target = 1080
            opts.inSampleSize = maxOf(opts.outWidth / target, opts.outHeight / target, 1)
            opts.inJustDecodeBounds = false
            bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            val bmp = bitmap
            if (bmp == null) {
                Toast.makeText(this, "图片读取失败", Toast.LENGTH_SHORT).show()
                finish(); return
            }

            image.setImageBitmap(bmp)
            image.post { fitImage() }

            scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    matrix.postScale(detector.scaleFactor, detector.scaleFactor, detector.focusX, detector.focusY)
                    fixTrans()
                    image.imageMatrix = matrix
                    return true
                }
            })

            image.setOnTouchListener { _, e ->
                scaleDetector.onTouchEvent(e)
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { mode = 1; lastX = e.x; lastY = e.y }
                    MotionEvent.ACTION_MOVE -> {
                        if (mode == 1 && !scaleDetector.isInProgress) {
                            matrix.postTranslate(e.x - lastX, e.y - lastY)
                            fixTrans()
                            image.imageMatrix = matrix
                            lastX = e.x; lastY = e.y
                        }
                    }
                    MotionEvent.ACTION_UP -> mode = 0
                }
                true
            }

            findViewById<TextView>(R.id.crop_close).setOnClickListener { finish() }
            findViewById<TextView>(R.id.crop_reset).setOnClickListener { fitImage() }
            findViewById<TextView>(R.id.crop_ok).setOnClickListener { cropAndSave() }
        } catch (t: Throwable) {
            Log.e("CropActivity", "fatal", t)
            Toast.makeText(this, "打开图片失败: ${t.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun fitImage() {
        val bmp = bitmap ?: return
        val vw = image.width.toFloat()
        val vh = image.height.toFloat()
        if (vw <= 0 || vh <= 0) return
        val scale = maxOf(vw / bmp.width, vh / bmp.height)
        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate((vw - bmp.width * scale) / 2f, (vh - bmp.height * scale) / 2f)
        image.imageMatrix = matrix
    }

    private fun fixTrans() {
        val bmp = bitmap ?: return
        val v = FloatArray(9); matrix.getValues(v)
        val bw = bmp.width * v[Matrix.MSCALE_X]
        val bh = bmp.height * v[Matrix.MSCALE_Y]
        if (bw < image.width) v[Matrix.MTRANS_X] = (image.width - bw) / 2f
        else v[Matrix.MTRANS_X] = v[Matrix.MTRANS_X].coerceIn(image.width - bw, 0f)
        if (bh < image.height) v[Matrix.MTRANS_Y] = (image.height - bh) / 2f
        else v[Matrix.MTRANS_Y] = v[Matrix.MTRANS_Y].coerceIn(image.height - bh, 0f)
        matrix.setValues(v)
    }

    private fun cropAndSave() {
        try {
            val bmp = bitmap ?: return
            val v = FloatArray(9); matrix.getValues(v)
            val sx = v[Matrix.MSCALE_X]; val sy = v[Matrix.MSCALE_Y]
            val tx = v[Matrix.MTRANS_X]; val ty = v[Matrix.MTRANS_Y]
            val fr = frameView.rect
            val srcL = ((fr.left - tx) / sx).toInt().coerceIn(0, bmp.width)
            val srcT = ((fr.top - ty) / sy).toInt().coerceIn(0, bmp.height)
            val srcR = ((fr.right - tx) / sx).toInt().coerceIn(0, bmp.width)
            val srcB = ((fr.bottom - ty) / sy).toInt().coerceIn(0, bmp.height)
            val w = srcR - srcL; val h = srcB - srcT
            if (w < 100 || h < 100) {
                Toast.makeText(this, "请调整裁剪框", Toast.LENGTH_SHORT).show(); return
            }
            val cropped = Bitmap.createBitmap(bmp, srcL, srcT, w, h)
            val outPath = intent.getStringExtra(EXTRA_OUT) ?: return
            FileOutputStream(File(outPath)).use { cropped.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            setResult(Activity.RESULT_OK)
            finish()
        } catch (t: Throwable) {
            Toast.makeText(this, "保存失败: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
