package com.example.detectmangodisease

import android.app.Activity
import android.content.Context
import android.content.Context.BATTERY_SERVICE
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.BatteryManager
import android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER
import android.os.Bundle
import android.provider.MediaStore
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.example.detectmangodisease.api.IEndpoint
import com.example.detectmangodisease.api.NetworkUtils
import com.example.detectmangodisease.databinding.FragmentHomeBinding
import com.example.detectmangodisease.databinding.FragmentMonitoringBinding
import com.example.detectmangodisease.dto.ResponsePredict
import com.example.detectmangodisease.dto.SettingsDTO
import com.example.detectmangodisease.ml.ModelSaved
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import retrofit2.Call
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import retrofit2.Callback
import retrofit2.Response
import retrofit2.http.Multipart
import java.io.File

class MonitoringFragment : Fragment() {

    private lateinit var binding: FragmentMonitoringBinding

    private lateinit var settings: SettingsDTO

    private lateinit var batteryManager: BatteryManager

    private var imageSize = 256

    private var allImages = ArrayList<Uri>()


    private var allTimes = ArrayList<Long>()
    private var allBatteryCount = ArrayList<Long>()

    private var PICK_IMAGES_CODE = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_monitoring, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentMonitoringBinding.bind(view)
        batteryManager = requireContext().getSystemService(BATTERY_SERVICE) as BatteryManager

        // Enabled if allImages.size > 0
        enableTestButton(false)

        binding.buttonSelect.setOnClickListener {
            pickImagesIntent()
        }
        binding.testButton.setOnClickListener {
            runImages()
        }
        getSettings()
        setSettingsInFragment()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == PICK_IMAGES_CODE && resultCode == Activity.RESULT_OK) {
            if(data!!.clipData != null) {
                val countImages = data.clipData!!.itemCount
                allImages = ArrayList()
                for(i in 0 until countImages) {
                    val imageUri = data.clipData!!.getItemAt(i).uri
                    allImages.add(imageUri)
                }
                println("All images")
                println(allImages.size)

                setCountSelectedImages(allImages.size)
                enableTestButton(allImages.size > 0)
            }
        }
    }
    private fun setCountSelectedImages(count: Int) {
        binding.buttonSelect.text = "Selecionar (${count})"
    }
    private fun enableTestButton(enable: Boolean) {
        binding.testButton.isEnabled = enable;
    }
    private fun pickImagesIntent() {
        val intent = Intent()
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Selecione as imagens"), PICK_IMAGES_CODE)
    }
    private suspend fun classifyImageInServer(filePart: RequestBody) {
//        GlobalScope.launch(context = Dispatchers.IO) {
            val retrofitClient = NetworkUtils.getRetrofitInstance(
                "https://354a-2804-14d-1289-a118-8d1-3337-6b15-22a2.ngrok.io/api/"
            )

            val endpoint = retrofitClient.create(IEndpoint::class.java)

            val response = endpoint.predictImage(filePart)
            if(response.isSuccessful) {
                println(response.body())
            }
            if(!response.isSuccessful) {
                println(response.errorBody()?.charStream()?.readText())
            }
//        }

    }
    private fun classifyImageLocal(image: Bitmap) {
        var model = ModelSaved.newInstance(requireContext())
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

        model.close()
    }
    private suspend fun classifyImage(image: Bitmap, index: Int) {
        if(settings.modelUsed == SettingsDTO.TypeModel.LOCAL) {
            println("Local------------")
            var imageScaled = Bitmap.createScaledBitmap(image, imageSize, imageSize, false)

            classifyImageLocal(imageScaled)
        } else {
            println("Server--------------")
            val stream = requireActivity().contentResolver.openInputStream(allImages[index])
            val request = RequestBody.create(MediaType.parse("image/*"), stream?.readBytes())
            var builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            builder.addFormDataPart("file", "data.jpeg", request)
            classifyImageInServer(builder.build())
        }
    }

    private fun monitoringAllImages() {
        GlobalScope.launch(context = Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                binding.testButton.isEnabled = false
                binding.progressBar.isVisible = true
                binding.buttonSelect.isEnabled = false
            }
            for(i in 0 until allImages.size) {
                try {
                    var image = MediaStore.Images.Media.getBitmap(requireActivity().contentResolver, allImages[i]) as Bitmap

                    var timeBefore = Date().time
                    var batteryBefore = batteryManager.getLongProperty(BATTERY_PROPERTY_CHARGE_COUNTER)

                    classifyImage(image, i)

                    allTimes.add(Date().time - timeBefore)
                    allBatteryCount.add(batteryBefore - batteryManager.getLongProperty(BATTERY_PROPERTY_CHARGE_COUNTER))
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                binding.testButton.isEnabled = true
                binding.progressBar.isVisible = false
                binding.buttonSelect.isEnabled = true
            }
            saveResults()
        }
    }

    private fun runImages() {
        monitoringAllImages()
    }

    private fun getFileName(): String {
        if(settings.modelUsed == SettingsDTO.TypeModel.LOCAL) {
            return getString(R.string.result_plain_mode)
        }
        var fileName = ""
        if(settings.monitored == SettingsDTO.TypeMonitored.WIFI) {
            fileName = getString(R.string.result_wifi)
        } else if(settings.monitored == SettingsDTO.TypeMonitored.FOUR_G) {
            fileName = getString(R.string.result_four_g)
        } else {
            fileName = getString(R.string.result_three_g)
        }
        return fileName
    }
    private fun saveResults() {
        var sharedPref = requireActivity()
            .getSharedPreferences(getFileName(), Context.MODE_PRIVATE)
        if(sharedPref != null) {
            val gson = Gson()
            val jsonTimes = gson.toJson(allTimes)
            val jsonBattery = gson.toJson(allBatteryCount)

            with(sharedPref.edit()) {
                putString("times", jsonTimes)
                putString("batteries", jsonBattery)
                commit()
            }
        }
    }

    private fun setSettingsInFragment() {
        binding.monitoringModelUsedText.text = settings.modelUsed.toString()
        binding.monitoringText.text = settings.monitored.toString()
    }
    private fun getSettings() {
        var sharedPref = requireActivity()
            .getSharedPreferences(getString(R.string.settings_file), Context.MODE_PRIVATE)

        settings = SettingsDTO()
        settings.modelUsed = SettingsDTO.TypeModel
            .valueOf(sharedPref.getString("modelUsed", "LOCAL").toString())

        settings.monitored = SettingsDTO.TypeMonitored
            .valueOf(sharedPref.getString("monitored", "MODE_PLAIN").toString())

    }
}
