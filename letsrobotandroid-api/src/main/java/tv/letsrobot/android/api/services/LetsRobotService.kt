package tv.letsrobot.android.api.services

import android.app.Service
import android.content.Intent
import android.os.*
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.runBlocking
import tv.letsrobot.android.api.enums.LogLevel
import tv.letsrobot.android.api.interfaces.ComponentEventListener
import tv.letsrobot.android.api.interfaces.ComponentEventObject
import tv.letsrobot.android.api.interfaces.IComponent

/**
 * The main LetsRobot control service.
 * This handles the lifecycle and communication to components that come from outside the sdk
 */
class LetsRobotService : Service(), ComponentEventListener {
    /**
     * Message handler for components that we are controlling.
     * Best thing to do after is to push it to the service handler for processing,
     * as this could be from any thread
     */
    override fun handleMessage(eventObject: ComponentEventObject) {
        handler.obtainMessage(EVENT_BROADCAST, eventObject).sendToTarget()
    }

    private var running = false



    /**
     * Target we publish for clients to send messages to MessageHandler.
     */
    private lateinit var mMessenger: Messenger
    private var handlerThread : HandlerThread = HandlerThread("LetsRobotControl").also { it.start() }

    private val componentList = ArrayList<IComponent>()
    private val activeComponentList = ArrayList<IComponent>()

    val handler = object : Handler(handlerThread.looper) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                START ->
                    runBlocking { enable() }
                STOP ->
                    runBlocking { disable() }
                ATTACH_COMPONENT -> {
                    (msg.obj as? IComponent)?.let {
                        addToLifecycle(it)
                    }
                }
                DETACH_COMPONENT -> {
                    (msg.obj as? IComponent)?.let {
                        removeFromLifecycle(it)
                    }
                }
                RESET -> {
                    runBlocking {
                        reset()
                    }
                }
                EVENT_BROADCAST ->{
                    runBlocking {
                        sendToComponents(msg)
                    }
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    private fun sendToComponents(msg: Message) {
        val obj = msg.obj as? ComponentEventObject
        var targetFilter : Int? = null
        obj?.let {
            if((obj.source as? IComponent)?.getType() != obj.type){
                //send a message to all components of type obj.type
                targetFilter = obj.type
            }
        }

        activeComponentList.forEach { component ->
            targetFilter?.takeIf { component.getType() != it }
                    ?: component.dispatchMessage(msg)
        }
    }

    /**
     * Reset the service. If running, we will disable, reload, then start again
     */
    private suspend fun reset() {
        if(running) {
            disable()
            reload()
            enable()
        }
        else{
            reload()
        }
    }

    /**
     * Reload the settings and prep for start
     */
    private fun reload() {

    }

    private fun addToLifecycle(component: IComponent) {
        if(!componentList.contains(component))
            componentList.add(component)
    }

    private fun removeFromLifecycle(component: IComponent) {
        componentList.remove(component)
    }

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    override fun onBind(intent: Intent): IBinder? {
        Toast.makeText(applicationContext, "binding", Toast.LENGTH_SHORT).show()
        mMessenger = Messenger(handler)
        emitState()
        return mMessenger.binder
    }

    suspend fun enable(){
        Toast.makeText(applicationContext, "Starting LetRobot Controller", Toast.LENGTH_SHORT).show()
        activeComponentList.clear()
        activeComponentList.addAll(componentList)
        activeComponentList.forEach{
            it.setEventListener(this)
            it.enable().await()
        }
        setState(true)
    }

    suspend fun disable(){
        Toast.makeText(applicationContext, "Stopping LetRobot Controller", Toast.LENGTH_SHORT).show()
        activeComponentList.forEach{
            it.disable().await()
            it.setEventListener(null)
        }
        setState(false)
    }

    fun setState(value : Boolean){
        running = value
        emitState()
    }

    private fun emitState() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(
                Intent(SERVICE_STATUS_BROADCAST).also {
                    it.putExtra("value", running)
                }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId).also {
            handler.obtainMessage(RESET).sendToTarget()
        }
    }

    companion object {
        const val START = 1
        const val STOP = 2
        const val RESET = 4
        const val ATTACH_COMPONENT = 5
        const val DETACH_COMPONENT = 6
        const val EVENT_BROADCAST = 7
        const val SERVICE_STATUS_BROADCAST = "tv.letsrobot.android.api.ServiceStatus"
        lateinit var logLevel: LogLevel
    }
}