package com.example.detectmangodisease

import android.app.Activity
import android.content.Context
import android.content.Context.BATTERY_SERVICE
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.BatteryManager
import android.os.BatteryManager.*
import android.os.Bundle
import android.provider.MediaStore
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import com.example.detectmangodisease.api.IEndpoint
import com.example.detectmangodisease.api.NetworkUtils
import com.example.detectmangodisease.databinding.FragmentHomeBinding
import com.example.detectmangodisease.databinding.FragmentMonitoringBinding
import com.example.detectmangodisease.dto.ResponsePredict
import com.example.detectmangodisease.dto.SettingsDTO
import com.example.detectmangodisease.ml.ModelSaved
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
import java.lang.reflect.Type
import kotlin.math.abs
import kotlin.math.round

class MonitoringFragment : Fragment() {

    private lateinit var binding: FragmentMonitoringBinding

    private lateinit var settings: SettingsDTO

    private lateinit var batteryManager: BatteryManager

    private var imageSize = 256

    private var allImages = ArrayList<Uri>()


    private var allTimes = ArrayList<Long>()

    private var PICK_IMAGES_CODE = 0

    private var batteryHashMap = hashMapOf<String, Long>("beforeAmps" to 0L, "beforePercent" to 0L, "afterAmps" to 0L, "afterPercent" to 0L)

    private lateinit var model: ModelSaved
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
        if(settings.modelUsed == SettingsDTO.TypeModel.LOCAL) {
            model = ModelSaved.newInstance(requireContext())
        }
        setSettingsInFragment()
        setAllResultsInView()
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

        try {
            val retrofitClient = NetworkUtils.getRetrofitInstance(
                "http://ec2-3-83-154-142.compute-1.amazonaws.com/api/"
            )
            val endpoint = retrofitClient.create(IEndpoint::class.java)

            val response = endpoint.predictImage(filePart)
            if(response.isSuccessful) {
                println(response.body())
            }
            if(!response.isSuccessful) {
                println(response.errorBody()?.charStream()?.readText())
            }
        } catch (e: java.lang.Exception) {
            println(e)
        }
    }
    private fun classifyImageLocal(image: Bitmap) {
//        var model = ModelSaved.newInstance(requireContext())
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

//        model.close()
    }
    private suspend fun classifyImage(image: Bitmap, index: Int) {
        if(settings.modelUsed == SettingsDTO.TypeModel.LOCAL) {
            var imageScaled = Bitmap.createScaledBitmap(image, imageSize, imageSize, false)

            classifyImageLocal(imageScaled)
        } else {
            val stream = requireActivity().contentResolver.openInputStream(allImages[index])
            val request = RequestBody.create(MediaType.parse("image/*"), stream?.readBytes())
            var builder = MultipartBody.Builder().setType(MultipartBody.FORM)

            builder.addFormDataPart("file", "data.jpeg", request)
            classifyImageInServer(builder.build())
        }
    }

    private fun monitoringAllImages() {
        GlobalScope.launch(context = Dispatchers.IO) {
            val total = allImages.size * binding.amountTests.text.toString().toInt()

            withContext(Dispatchers.Main) {
                binding.testButton.isEnabled = false
                binding.progressBar.isVisible = true
                binding.buttonSelect.isEnabled = false
                binding.amountTests.isEnabled = false
                binding.progressNumber.text = "0/${total}"
                batteryHashMap["beforeAmps"] = batteryManager.getLongProperty(BATTERY_PROPERTY_CHARGE_COUNTER)
                batteryHashMap["beforePercent"] = batteryManager.getLongProperty(BATTERY_PROPERTY_CAPACITY)
            }
            var currentStep = 0
            for (k in 0 until binding.amountTests.text.toString().toInt()) {
                for(i in 0 until allImages.size) {
                    try {
                        var image = MediaStore.Images.Media.getBitmap(requireActivity().contentResolver, allImages[i]) as Bitmap

                        var timeBefore = Date().time

                        classifyImage(image, i)

                        allTimes.add(Date().time - timeBefore)

                        currentStep++
                        withContext(Dispatchers.Main) {
                            println(currentStep)
                            binding.progressNumber.text = "$currentStep/$total"
                        }

                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
            withContext(Dispatchers.Main) {
                binding.testButton.isEnabled = true
                binding.progressBar.isVisible = false
                binding.buttonSelect.isEnabled = true
                binding.amountTests.isEnabled = true
//                batteryHashMap["afterAmps"] = batteryManager.getLongProperty(BATTERY_PROPERTY_CHARGE_COUNTER)
//                batteryHashMap["afterPercent"] = batteryManager.getLongProperty(BATTERY_PROPERTY_CAPACITY)
//                saveResults()
//                setAllResultsInView()
            }
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

    private fun setAllResultsInView() {
        setResultsInView(binding.resultLatencyPlainMode, binding.resultBatteryPlainMode, getString(R.string.result_plain_mode))
        setResultsInView(binding.resultLatencyWifi, binding.resultBatteryWifi, getString(R.string.result_wifi))
        setResultsInView(binding.resultLatencyFourG, binding.resultBatteryFourG, getString(R.string.result_four_g))
        setResultsInView(binding.resultLatencyThreeG, binding.resultBatteryThreeG, getString(R.string.result_three_g))
    }
    private fun setResultsInView(textViewTimes: TextView, textViewBatteries: TextView, fileName: String) {
        val (resultTimes, resultBattery) = getResults(fileName)
        if(resultTimes.size > 0) {
            val meanTimes = String.format("%.2f",resultTimes.average())
            textViewTimes.text = "Latência Média (ms): $meanTimes"
        } else {
            textViewTimes.text = "Latência Média (ms): NÃO CALCULADO"
        }
        if(resultTimes.size > 0) {
//            val meanBattery = String.format("%.8f", resultBatteries.average())
            textViewBatteries.text = "Uso de bateria (mAh): $resultBattery"
        } else {
            textViewBatteries.text = "Uso de bateria (mAh): NÃO CALCULADO"
        }
    }
    private fun getResults(fileName: String): Pair<ArrayList<Long>, Long> {
        var resultTimes = ArrayList<Long>()
        var resultBattery = 0L
        var sharedPref = requireActivity()
            .getSharedPreferences(fileName, Context.MODE_PRIVATE)
        if(sharedPref != null) {
            val gson = Gson()

            val resultTimesText = sharedPref.getString("times", null)
            val resultBatteryText = sharedPref.getString("batteryAmps", null)
            if(resultTimesText != null) {
                resultTimes = gson.fromJson(resultTimesText, java.util.ArrayList<Long>().javaClass)
            }
            if(resultBatteryText != null) {
                resultBattery = resultBatteryText.toLong()
            }
        }
        return Pair(resultTimes, resultBattery)
    }

    private fun saveResults() {
        var sharedPref = requireActivity()
            .getSharedPreferences(getFileName(), Context.MODE_PRIVATE)
        if(sharedPref != null) {
            val gson = Gson()
            val jsonTimes = gson.toJson(allTimes)

            with(sharedPref.edit()) {
                putString("times", jsonTimes)
                putString("batteryAmps", (batteryHashMap.getValue("beforeAmps") - batteryHashMap.getValue("afterAmps")).toString())
                putString("batteryBeforeAmps", batteryHashMap["beforeAmps"].toString())
                putString("batteryBeforePercent", batteryHashMap["beforePercent"].toString())
                putString("batteryAfterAmps", batteryHashMap["afterAmps"].toString())
                putString("batteryAfterPercent", batteryHashMap["afterPercent"].toString())
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

    override fun onDestroyView() {
        super.onDestroyView()
        if(::model.isInitialized) {
            model.close()
        }
    }
}

