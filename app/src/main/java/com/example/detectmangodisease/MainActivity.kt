package com.example.detectmangodisease

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.detectmangodisease.databinding.ActivityMainBinding
import com.example.detectmangodisease.ml.ModelSaved
import com.google.android.material.resources.TextAppearance
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val imageSize = 256

    private val classes = arrayOf("Anthracnose",
        "Bacterial Canker", "Cutting Weevil", "Die Back",
        "Gall Midge", "Healthy", "Powdery Mildew", "Sooty Mould"
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.helpIcon.setOnClickListener{
            Toast.makeText(this, "Help", Toast.LENGTH_SHORT).show()
        }
        binding.takePicture.setOnClickListener {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                startActivityForResult(cameraIntent, 3);
            } else {
                requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 100);
            }
        }

        binding.launchGallery.setOnClickListener {
            val galleryIntent =
                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(galleryIntent, 1)
        }

        binding.titleResult.text = "Selecione ou tire uma foto"
        binding.titleResult.setTextAppearance(androidx.appcompat.R.style.TextAppearance_AppCompat_Medium)

        binding.bottomNavigation.setOnItemSelectedListener {
            // TODO mudar para o fragment
            when(it.itemId) {
                R.id.item_home -> Toast.makeText(this, "Home", Toast.LENGTH_SHORT).show()
                R.id.item_history -> Toast.makeText(this, "History", Toast.LENGTH_SHORT).show()
                else -> {
                    Toast.makeText(this, "Home", Toast.LENGTH_SHORT).show()
                }
            }
            true
        }

    }


//    private fun replaceFragment(fragment: Fragment) {
//        val fragmentManager = supportFragmentManager
//        val fragmentTransaction = fragmentManager.beginTransaction()
//
//        fragmentTransaction.replace(R.id.frame_layout, fragment)
//        fragmentTransaction.commit()
//    }
    fun classifyImage(image: Bitmap) {
        val model = ModelSaved.newInstance(applicationContext)

        // Creates inputs for reference.
        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, imageSize, imageSize, 3), DataType.FLOAT32)
        val byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize *3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(imageSize * imageSize)

        image.getPixels(intValues, 0, image.width, 0, 0, image.width, image.height)

        var pixel = 0
        for (i in 0 until imageSize) {
            for (j in 0 until imageSize) {
                val value = intValues[pixel++]
                byteBuffer.putFloat((value shr 16 and 0xFF) * (1 / 255f))
                byteBuffer.putFloat((value shr 8 and 0xFF) * (1 / 255f))
                byteBuffer.putFloat((value and 0xFF) * (1 / 255f))
            }
        }
        inputFeature0.loadBuffer(byteBuffer)

        // Runs model inference and gets result.
        val outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer

        val confidences = outputFeature0.floatArray

        println(Arrays.toString(confidences))
        val maxValue = confidences.maxOrNull()?:0.0
        val maxIndex = confidences.indexOfFirst { it == maxValue }

        binding.titleResult.text = "Resultado: "
        binding.titleResult.setTextAppearance(androidx.appcompat.R.style.TextAppearance_AppCompat_Large)
        binding.result.text = classes[maxIndex]

        model.close()

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(resultCode == RESULT_OK) {
            if(requestCode == 3) {
                var image = data?.extras?.get("data") as Bitmap
                var dimension = Math.min(image.width, image.height)
                image = ThumbnailUtils.extractThumbnail(image, dimension, dimension)

                binding.imageView.setImageBitmap(image)

                image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false)

                classifyImage(image)
            } else {
                val dat = data?.data
                var image: Bitmap? = null
                try {
                    image = MediaStore.Images.Media.getBitmap(contentResolver, dat) as Bitmap
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                binding.imageView.setImageBitmap(image)

                if(image != null) {
                    image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false)
                    classifyImage(image)
                }

            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}