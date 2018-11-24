package tv.letsrobot.controller.android.activities

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.letsrobot.controller.android.R
import tv.letsrobot.android.api.Core
import tv.letsrobot.android.api.interfaces.CommunicationInterface
import tv.letsrobot.android.api.utils.StoreUtil

class SplashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        // Setup App before initializing anything, then go back to do permissions flow
        // and to do device setup
        if(!StoreUtil.getConfigured(this)){
            finish()
            startActivity(Intent(this, ManualSetupActivity::class.java))
            return
        }

        Core.initDependencies(this){
            runOnUiThread{
                next()
            }
        }
    }

    //TODO replace with co-routine once that is stable and stops breaking Android Studio
    private fun next() {
        //Check permissions. break out if that returns false
        if(!checkPermissions()){
            return
        }
        //Setup device. break out if not setup, or if error occurred
        setupDevice()?.let {
            if(!it){
                //setup not complete
                return
            }
        } ?: run{
            //Something really bad happened here. Not sure how we continue
            setupError()
            return
        }
        //All checks are done. Lets startup the activity!
        finish()
        startActivity(Intent(this, MainRobotActivity::class.java))
    }

    /**
     * Show some setup error message. Allow the user to attempt setup again
     */
    private fun setupError() {
        Toast.makeText(this
                , "Something happened while trying to setup. Please try again"
                , Toast.LENGTH_LONG).show()
        StoreUtil.setConfigured(this, false)
        finish()
        startActivity(Intent(this, ManualSetupActivity::class.java))
    }

    private var pendingDeviceSetup: CommunicationInterface? = null

    private var pendingResultCode: Int = -1

    private fun setupDevice(): Boolean? {
        val commType = StoreUtil.getCommunicationType(this) // :CommunicationType?
        commType?.let {
            val clazz = it.getInstantiatedClass
            clazz?.let {
                return if(it.needsSetup(this)){
                    val tmpCode = it.setupComponent(this)
                    //Sometimes we still need setup without a UI. Will return -1 if that is the case
                    if(tmpCode == -1){
                        true
                    }
                    else{
                        pendingResultCode = tmpCode
                        pendingDeviceSetup = it
                        false
                    }
                } else{
                    true
                }
            }
        }
        return null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(checkPermissions()){
            next()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        //Check if result was due to a pending interface setup
        pendingDeviceSetup?.takeIf { pendingResultCode == requestCode}?.let {
            //relay info to interface
            it.receivedComponentSetupDetails(this, data)
            pendingDeviceSetup = null
            pendingResultCode = -1
            next()
        }
    }

    private val requestCode = 1002

    private fun checkPermissions() : Boolean{
        val list = ArrayList<String>(arrayListOf( //TODO selectively add based on settings
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        ))
        val permissionsToAccept = ArrayList<String>()
        for (perm in list){
            if(ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED){
                permissionsToAccept.add(perm)
            }
        }

        return if(!permissionsToAccept.isEmpty()){
            ActivityCompat.requestPermissions(this,
                    permissionsToAccept.toArray(Array(0) {""}),
                    requestCode)
            false
        }
        else{
            true
        }
    }
}