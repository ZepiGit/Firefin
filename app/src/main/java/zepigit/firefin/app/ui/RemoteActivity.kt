package zepigit.firefin.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import zepigit.firefin.app.R
import zepigit.firefin.app.ServiceLocator
import zepigit.firefin.app.data.RemoteSession

/** Server-side remote control: list sessions and send Play/Pause/Stop. */
class RemoteActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var adapter: SessionsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote)
        val list = findViewById<RecyclerView>(R.id.sessions)
        val empty = findViewById<TextView>(R.id.empty)
        adapter = SessionsAdapter { session, command ->
            scope.launch {
                runCatching { ServiceLocator.client.sendCommand(session.id, command) }
                    .onFailure {
                        Toast.makeText(this@RemoteActivity, it.message ?: "Fehler", Toast.LENGTH_LONG).show()
                    }
                refresh()
            }
        }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        refresh()
    }

    private fun refresh() {
        scope.launch {
            findViewById<android.widget.ProgressBar>(R.id.progress).visibility = View.VISIBLE
            try {
                val sessions = ServiceLocator.client.sessions()
                    .filter { it.controllable && it.deviceId != ServiceLocator.session.deviceId }
                adapter.submit(sessions)
                findViewById<TextView>(R.id.empty).visibility =
                    if (sessions.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Toast.makeText(this@RemoteActivity, e.message ?: "Fehler", Toast.LENGTH_LONG).show()
            } finally {
                findViewById<android.widget.ProgressBar>(R.id.progress).visibility = View.GONE
            }
        }
    }
}

private class SessionsAdapter(
    private val onCommand: (RemoteSession, String) -> Unit,
) : RecyclerView.Adapter<SessionsAdapter.Holder>() {

    private val sessions = mutableListOf<RemoteSession>()

    fun submit(newSessions: List<RemoteSession>) {
        sessions.clear()
        sessions.addAll(newSessions)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = sessions.size
    override fun getItemId(position: Int): Long = sessions[position].id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.view_session, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val session = sessions[position]
        holder.device.text = session.deviceName.ifBlank { "Gerät" }
        holder.info.text = buildString {
            if (session.userName.isNotEmpty()) append(session.userName)
            if (session.nowPlaying.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append("▶ ").append(session.nowPlaying)
            }
        }
        holder.playPause.setOnClickListener { onCommand(session, "PlayPause") }
        holder.stop.setOnClickListener { onCommand(session, "Stop") }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val device: TextView = view.findViewById(R.id.deviceName)
        val info: TextView = view.findViewById(R.id.sessionInfo)
        val playPause: Button = view.findViewById(R.id.playPauseButton)
        val stop: Button = view.findViewById(R.id.stopButton)
    }
}
