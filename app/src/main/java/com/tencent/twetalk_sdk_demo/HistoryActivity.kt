package com.tencent.twetalk_sdk_demo

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.tencent.twetalk_sdk_demo.adapter.ChatSessionAdapter
import com.tencent.twetalk_sdk_demo.data.ChatSession
import com.tencent.twetalk_sdk_demo.databinding.ActivityHistoryBinding

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var sessionAdapter: ChatSessionAdapter
    private val allSessions = mutableListOf<ChatSession>()
    private val filteredSessions = mutableListOf<ChatSession>()
    
    private var currentFilter = "ALL"
    private var sortByTime = true // true: 最新在前, false: 最旧在前

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupSearchAndFilter()
        loadMockData()
        updateUI()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.btnDeleteAll.setOnClickListener {
            showDeleteAllDialog()
        }
    }

    private fun setupRecyclerView() {
        sessionAdapter = ChatSessionAdapter(
            onItemClick = { session -> openSessionDetail(session) },
            onDeleteClick = { session -> showDeleteSessionDialog(session) }
        )
        
        binding.recyclerViewHistory.apply {
            adapter = sessionAdapter
            layoutManager = LinearLayoutManager(this@HistoryActivity)
        }
    }

    private fun setupSearchAndFilter() {
        // 搜索功能
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterSessions()
            }
        })

        // 筛选功能
        binding.chipGroupConnection.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when {
                binding.chipAll.isChecked -> "ALL"
                binding.chipWebSocket.isChecked -> "WEBSOCKET"
                binding.chipTRTC.isChecked -> "TRTC"
                else -> "ALL"
            }
            filterSessions()
        }

        // 排序功能
        binding.btnSort.setOnClickListener {
            sortByTime = !sortByTime
            val iconRes = if (sortByTime) R.drawable.ic_sort_desc else R.drawable.ic_sort_asc
            binding.btnSort.setIconResource(iconRes)
            
            val sortText = if (sortByTime) "按时间降序" else "按时间升序"
            Toast.makeText(this, sortText, Toast.LENGTH_SHORT).show()
            
            filterSessions()
        }
    }

    private fun loadMockData() {
        // 模拟历史记录数据
        val currentTime = System.currentTimeMillis()
        
        allSessions.addAll(listOf(
            ChatSession(
                title = "AI 语音助手对话",
                connectionType = "WEBSOCKET",
                audioFormat = "OPUS",
                startTime = currentTime - 3600000, // 1小时前
                endTime = currentTime - 3300000,
                messageCount = 15,
                lastMessage = "感谢您的使用，再见！",
                summary = "讨论了天气和日程安排"
            ),
            ChatSession(
                title = "技术咨询会话",
                connectionType = "TRTC",
                audioFormat = "PCM",
                startTime = currentTime - 86400000, // 1天前
                endTime = currentTime - 86100000,
                messageCount = 8,
                lastMessage = "这个问题我需要进一步了解...",
                summary = "关于Android开发的技术问题"
            ),
            ChatSession(
                title = "日常聊天",
                connectionType = "WEBSOCKET",
                audioFormat = "OPUS",
                startTime = currentTime - 172800000, // 2天前
                endTime = currentTime - 172500000,
                messageCount = 23,
                lastMessage = "今天的天气真不错呢",
                summary = "轻松愉快的日常对话"
            ),
            ChatSession(
                title = "学习辅导",
                connectionType = "TRTC",
                audioFormat = "OPUS",
                startTime = currentTime - 259200000, // 3天前
                endTime = currentTime - 258900000,
                messageCount = 12,
                lastMessage = "希望这些解释对您有帮助",
                summary = "数学和物理问题解答"
            ),
            ChatSession(
                title = "工作讨论",
                connectionType = "WEBSOCKET",
                audioFormat = "PCM",
                startTime = currentTime - 345600000, // 4天前
                endTime = currentTime - 345300000,
                messageCount = 18,
                lastMessage = "项目进度看起来很不错",
                summary = "项目规划和进度讨论"
            )
        ))
    }

    private fun filterSessions() {
        val searchText = binding.etSearch.text.toString().lowercase()
        
        filteredSessions.clear()
        filteredSessions.addAll(
            allSessions.filter { session ->
                // 连接类型筛选
                val matchesFilter = when (currentFilter) {
                    "ALL" -> true
                    else -> session.connectionType == currentFilter
                }
                
                // 搜索文本筛选
                val matchesSearch = if (searchText.isEmpty()) {
                    true
                } else {
                    session.title.lowercase().contains(searchText) ||
                    session.lastMessage.lowercase().contains(searchText) ||
                    session.summary.lowercase().contains(searchText)
                }
                
                matchesFilter && matchesSearch
            }.sortedWith { session1, session2 ->
                if (sortByTime) {
                    session2.startTime.compareTo(session1.startTime) // 降序
                } else {
                    session1.startTime.compareTo(session2.startTime) // 升序
                }
            }
        )
        
        updateUI()
    }

    private fun updateUI() {
        if (filteredSessions.isEmpty()) {
            binding.recyclerViewHistory.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
        } else {
            binding.recyclerViewHistory.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
            sessionAdapter.submitList(filteredSessions.toList())
        }
    }

    private fun openSessionDetail(session: ChatSession) {
        // 这里应该跳转到会话详情界面
        // 由于需求说明Agent不支持续聊，所以只能查看历史记录
        Toast.makeText(this, "查看会话详情: ${session.title}", Toast.LENGTH_SHORT).show()
        
        // 可以创建一个新的Activity来显示会话详情
        // val intent = Intent(this, SessionDetailActivity::class.java)
        // intent.putExtra("session_id", session.id)
        // startActivity(intent)
    }

    private fun showDeleteSessionDialog(session: ChatSession) {
        AlertDialog.Builder(this)
            .setTitle("删除会话")
            .setMessage("确定要删除会话「${session.title}」吗？")
            .setPositiveButton("删除") { _, _ ->
                deleteSession(session)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteSession(session: ChatSession) {
        allSessions.remove(session)
        filterSessions()
        Toast.makeText(this, "会话已删除", Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteAllDialog() {
        if (allSessions.isEmpty()) {
            Toast.makeText(this, "没有可删除的记录", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("删除全部记录")
            .setMessage("确定要删除所有聊天记录吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                deleteAllSessions()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteAllSessions() {
        allSessions.clear()
        filterSessions()
        Toast.makeText(this, "所有记录已删除", Toast.LENGTH_SHORT).show()
    }
}