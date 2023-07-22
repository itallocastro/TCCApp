package com.example.detectmangodisease

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Bundle
import android.provider.MediaStore
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.PermissionChecker
import androidx.core.content.PermissionChecker.checkSelfPermission
import com.example.detectmangodisease.databinding.FragmentHomeBinding
import com.example.detectmangodisease.dto.SettingsDTO
import com.example.detectmangodisease.ml.ModelSaved
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding


    private lateinit var settings: SettingsDTO

    private val imageSize = 256

    private val classes = arrayOf("Anthracnose",
        "Bacterial Canker", "Cutting Weevil", "Die Back",
        "Gall Midge", "Healthy", "Powdery Mildew", "Sooty Mould"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentHomeBinding.bind(view)

        binding.takePicture.setOnClickListener {
            if (checkSelfPermission(requireActivity(), Manifest.permission.CAMERA) == PermissionChecker.PERMISSION_GRANTED) {
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                startActivityForResult(cameraIntent, 3);
            } else {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 100);
            }
        }

        binding.launchGallery.setOnClickListener {
            val galleryIntent =
                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(galleryIntent, 1)
        }

        binding.titleResult.text = "Selecione ou tire uma foto"

        getSettings()
    }

    private fun getSettings() {
        var sharedPref = requireActivity()
            .getSharedPreferences(getString(R.string.settings_file), Context.MODE_PRIVATE)

        settings = SettingsDTO()
        settings.modelUsed = SettingsDTO.TypeModel
            .valueOf(sharedPref.getString("modelUsed", "LOCAL").toString())

        settings.monitored = SettingsDTO.TypeMonitored
            .valueOf(sharedPref.getString("monitored", "MODE_PLAIN").toString())

        println("---------Settings: ")
        println(settings.modelUsed.name)
        println(settings.monitored.name)
    }
    fun classifyImage(image: Bitmap) {
        val model = ModelSaved.newInstance(requireContext())

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
        binding.result.text = classes[maxIndex]

        model.close()

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(resultCode == AppCompatActivity.RESULT_OK) {
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
                    image = MediaStore.Images.Media.getBitmap(requireActivity().contentResolver, dat) as Bitmap
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

    override fun onDestroyView() {
        println("----------- Destroyed")
        super.onDestroyView()
    }
}