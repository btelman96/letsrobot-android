package tv.letsrobot.android.api.services

import android.content.*
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import tv.letsrobot.android.api.interfaces.IComponent
import tv.letsrobot.android.api.interfaces.ILetsRobotControl
import tv.letsrobot.android.api.models.Operation

/**
 * Binder for LetsRobot Service that allows us to put all of the communication code in one class
 */
class LetsRobotControlApi private constructor(
        anyContext: Context,
        private val context : Context = anyContext.applicationContext
) : ServiceConnection, ILetsRobotControl{

    private val serviceState: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>()
    }

    private val serviceConnectionStatus: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>()
    }

    private var mService: Messenger? = null

    override fun onServiceConnected(className: ComponentName, service: IBinder) {
        // This is called when the connection with the service has been
        // established, giving us the object we can use to
        // interact with the service.  We are communicating with the
        // service using a Messenger, so here we get a client-side
        // representation of that from the raw IBinder object.
        mService = Messenger(service)
        serviceConnectionStatus.value = Operation.OK
    }

    override fun onServiceDisconnected(className: ComponentName) {
        // This is called when the connection with the service has been
        // unexpectedly disconnected -- that is, its process crashed.
        mService = null
        serviceConnectionStatus.value = Operation.NOT_OK
    }

    @Throws(IllegalStateException::class)
    override fun enable() {
        serviceState.value = Operation.LOADING
        sendStateUnsafe(LetsRobotService.START)
    }

    @Throws(IllegalStateException::class)
    override fun disable() {
        serviceState.value = Operation.LOADING
        sendStateUnsafe(LetsRobotService.STOP)
    }

    @Throws(IllegalStateException::class)
    override fun reset() {
        sendStateUnsafe(LetsRobotService.RESET)
    }

    @Throws(IllegalStateException::class)
    private fun sendStateUnsafe(what : Int){
        mService?.send(Message.obtain(null, what)) ?: throw IllegalStateException()
    }

    override fun getServiceStateObserver(): LiveData<Int> {
        return serviceState
    }

    override fun getServiceConnectionStatusObserver(): LiveData<Int> {
        return serviceConnectionStatus
    }

    override fun attachToLifecycle(component: IComponent) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun detachFromLifecycle(component: IComponent) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private var receiver = Receiver(serviceState)

    override fun connectToService() {
        receiver = Receiver(serviceState)
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver,
                IntentFilter(LetsRobotService.SERVICE_STATUS_BROADCAST))
        Intent(context, LetsRobotService::class.java).also { intent ->
            context.bindService(intent, this, Context.BIND_AUTO_CREATE)
        }
    }

    override fun disconnectFromService() {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        context.unbindService(this)
    }

    class Receiver(val liveData: MutableLiveData<Int>) : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.takeIf { it.action == LetsRobotService.SERVICE_STATUS_BROADCAST }?.let {
                liveData.value = if(it.getBooleanExtra("value", false)){
                    Operation.OK
                }else{
                    Operation.NOT_OK
                }
            }
        }
    }

    companion object {
        fun getNewInstance(context: Context) : ILetsRobotControl{
            return LetsRobotControlApi(context)
        }
    }
}