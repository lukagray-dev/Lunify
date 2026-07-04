package com.android.lunify.duo.chat.ui

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.lunify.R
import com.android.lunify.databinding.DialogDuoChatBinding
import com.android.lunify.databinding.ItemDuoChatMessageBinding
import com.android.lunify.duo.chat.model.ChatMessage
import com.android.lunify.duo.chat.model.MessageStatus
import com.android.lunify.duo.chat.model.MessageType
import com.android.lunify.duo.ui.viewmodel.DuoViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

/**
 * Full-screen bottom sheet for Duo chat, using standard Android Views
 */
class DuoChatBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "DuoChatBottomSheet"
        fun newInstance() = DuoChatBottomSheet()
    }

    private var _binding: DialogDuoChatBinding? = null
    private val binding get() = _binding!!

    private val duoViewModel: DuoViewModel by activityViewModels()
    private var chatAdapter: ChatMessagesAdapter? = null
    private var currentPlayingPlayer: MediaPlayer? = null
    private var playingMessageId: String? = null

    // Permission launcher for audio recording
    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Audio permission granted")
            duoViewModel.onAudioPermissionGranted()
        } else {
            Log.d(TAG, "Audio permission denied")
            Toast.makeText(
                requireContext(),
                "Microphone permission is required for voice messages",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set style to handle keyboard correctly
        setStyle(STYLE_NORMAL, R.style.DuoChatBottomSheetTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogDuoChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBottomSheetBehavior()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
        duoViewModel.onChatOpened()
    }

    private fun setupBottomSheetBehavior() {
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            isHideable = true
        }
    }

    private fun setupRecyclerView() {
        val lm = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.rvMessages.layoutManager = lm
        chatAdapter = ChatMessagesAdapter(
            onPlayVoiceClick = { message -> playVoiceMessage(message) }
        )
        binding.rvMessages.adapter = chatAdapter
    }

    private fun setupListeners() {
        binding.btnCloseChat.setOnClickListener {
            dismiss()
        }

        binding.btnSendMessage.setOnClickListener {
            sendText()
        }

        binding.etMessageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendText()
                true
            } else false
        }

        // Typing notifier
        binding.etMessageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrEmpty()) {
                    duoViewModel.notifyTyping()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Microphone record button
        binding.btnVoiceRecord.setOnClickListener {
            checkAndRequestAudioPermission()
        }
    }

    private fun sendText() {
        val text = binding.etMessageInput.text.toString().trim()
        if (text.isNotEmpty()) {
            duoViewModel.sendChatMessage(text)
            binding.etMessageInput.text.clear()
        }
    }

    private fun checkAndRequestAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return
            }
        }
        duoViewModel.toggleVoiceRecording()
    }

    private fun observeViewModel() {
        // Observe messages list
        lifecycleScope.launch {
            duoViewModel.chatMessages.collectLatest { list ->
                chatAdapter?.submitList(list)
                if (list.isNotEmpty()) {
                    binding.rvMessages.smoothScrollToPosition(list.size - 1)
                }
                // Mark incoming messages as read since chat window is open
                duoViewModel.markMessagesAsRead()
            }
        }

        // Observe partner typing indicator
        lifecycleScope.launch {
            duoViewModel.isPartnerTyping.collectLatest { typing ->
                binding.tvTypingIndicator.visibility = if (typing) View.VISIBLE else View.GONE
            }
        }

        // Observe recording status
        lifecycleScope.launch {
            duoViewModel.isRecordingVoice.collectLatest { recording ->
                binding.recordingPanel.visibility = if (recording) View.VISIBLE else View.GONE
                binding.btnVoiceRecord.setImageResource(
                    if (recording) R.drawable.ic_stop else R.drawable.ic_mic
                )
            }
        }

        // Observe connection type text
        lifecycleScope.launch {
            duoViewModel.connectionTypeText.collectLatest { text ->
                binding.tvConnectionStatus.text = if (text.isNotEmpty()) {
                    "Connected via $text"
                } else {
                    "Connecting..."
                }
            }
        }

        // Listen for requests to trigger audio permission
        lifecycleScope.launch {
            duoViewModel.requestAudioPermission.collectLatest {
                checkAndRequestAudioPermission()
            }
        }
    }

    private fun playVoiceMessage(message: ChatMessage) {
        val voiceData = message.voiceData ?: return

        // Stop current playing audio
        if (playingMessageId == message.id) {
            stopVoicePlayback()
            return
        }

        stopVoicePlayback()

        try {
            val tempFile = File.createTempFile("voice_playback_", ".m4a", requireContext().cacheDir)
            tempFile.writeBytes(voiceData)

            val player = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    stopVoicePlayback()
                    tempFile.delete()
                }
            }
            currentPlayingPlayer = player
            playingMessageId = message.id
            chatAdapter?.setPlayingState(message.id, true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play voice message", e)
            Toast.makeText(requireContext(), "Failed to play audio", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopVoicePlayback() {
        currentPlayingPlayer?.apply {
            try {
                if (isPlaying) {
                    stop()
                }
            } catch (_: Exception) {}
            release()
        }
        currentPlayingPlayer = null
        
        playingMessageId?.let { id ->
            chatAdapter?.setPlayingState(id, false)
        }
        playingMessageId = null
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        stopVoicePlayback()
        duoViewModel.onChatClosed()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Simple RecyclerView Adapter for binding ChatMessage objects to Views
 */
class ChatMessagesAdapter(
    private val onPlayVoiceClick: (ChatMessage) -> Unit
) : RecyclerView.Adapter<ChatMessagesAdapter.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()
    private var playingMessageId: String? = null

    fun submitList(newList: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newList)
        notifyDataSetChanged()
    }

    fun setPlayingState(messageId: String, isPlaying: Boolean) {
        val oldPlayingId = playingMessageId
        playingMessageId = if (isPlaying) messageId else null
        
        // Notify changes to refresh play/pause icons
        messages.forEachIndexed { index, msg ->
            if (msg.id == messageId || msg.id == oldPlayingId) {
                notifyItemChanged(index)
            }
        }
    }

    class ViewHolder(val binding: ItemDuoChatMessageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDuoChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]

        if (message.isFromMe) {
            // Outgoing
            holder.binding.containerOutgoing.visibility = View.VISIBLE
            holder.binding.containerIncoming.visibility = View.GONE

            if (message.type == MessageType.VOICE) {
                holder.binding.tvOutgoingText.visibility = View.GONE
                holder.binding.voiceOutgoingContainer.visibility = View.VISIBLE
                
                val durationSec = message.voiceDuration / 1000
                holder.binding.tvOutgoingVoiceDuration.text = "Voice (0:${String.format("%02d", durationSec)})"
                
                val isPlaying = playingMessageId == message.id
                holder.binding.ivOutgoingVoicePlay.setImageResource(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                )
                holder.binding.voiceOutgoingContainer.setOnClickListener {
                    onPlayVoiceClick(message)
                }
            } else {
                holder.binding.tvOutgoingText.visibility = View.VISIBLE
                holder.binding.voiceOutgoingContainer.visibility = View.GONE
                holder.binding.tvOutgoingText.text = message.text
                holder.binding.voiceOutgoingContainer.setOnClickListener(null)
            }

            // Status message
            holder.binding.tvOutgoingStatus.text = when (message.status) {
                MessageStatus.SENDING -> "Sending..."
                MessageStatus.SENT -> "Sent"
                MessageStatus.DELIVERED -> "Delivered"
                MessageStatus.READ -> "Read"
                MessageStatus.FAILED -> "Failed"
            }
        } else {
            // Incoming
            holder.binding.containerOutgoing.visibility = View.GONE
            holder.binding.containerIncoming.visibility = View.VISIBLE

            holder.binding.tvIncomingSender.text = message.senderName

            if (message.type == MessageType.VOICE) {
                holder.binding.tvIncomingText.visibility = View.GONE
                holder.binding.voiceIncomingContainer.visibility = View.VISIBLE
                
                val durationSec = message.voiceDuration / 1000
                holder.binding.tvIncomingVoiceDuration.text = "Voice (0:${String.format("%02d", durationSec)})"
                
                val isPlaying = playingMessageId == message.id
                holder.binding.ivIncomingVoicePlay.setImageResource(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                )
                holder.binding.voiceIncomingContainer.setOnClickListener {
                    onPlayVoiceClick(message)
                }
            } else {
                holder.binding.tvIncomingText.visibility = View.VISIBLE
                holder.binding.voiceIncomingContainer.visibility = View.GONE
                holder.binding.tvIncomingText.text = message.text
                holder.binding.voiceIncomingContainer.setOnClickListener(null)
            }
        }
    }

    override fun getItemCount(): Int = messages.size
}
