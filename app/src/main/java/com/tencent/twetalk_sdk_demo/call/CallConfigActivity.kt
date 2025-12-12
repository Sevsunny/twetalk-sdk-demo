package com.tencent.twetalk_sdk_demo.call

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.tencent.twetalk_sdk_demo.BaseActivity
import com.tencent.twetalk_sdk_demo.R
import com.tencent.twetalk_sdk_demo.data.Contact
import com.tencent.twetalk_sdk_demo.databinding.ActivityCallConfigBinding
import com.tencent.twetalk_sdk_demo.databinding.DialogContactEditBinding

/**
 * 通话配置页面
 * 配置小程序信息和管理通讯录
 */
class CallConfigActivity : BaseActivity<ActivityCallConfigBinding>() {

    private lateinit var contactAdapter: ContactAdapter
    private val contacts = mutableListOf<Contact>()

    override fun getViewBinding() = ActivityCallConfigBinding.inflate(layoutInflater)

    override fun initView() {
        setupToolbar()
        setupWxaInfo()
        setupContactsList()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupWxaInfo() {
        // 加载已保存的配置
        binding.etWxaAppId.setText(CallConfigManager.getWxaAppId(this))
        binding.etWxaModelId.setText(CallConfigManager.getWxaModelId(this))

        // 自动保存
        binding.etWxaAppId.doAfterTextChanged { text ->
            CallConfigManager.setWxaAppId(this, text?.toString() ?: "")
        }

        binding.etWxaModelId.doAfterTextChanged { text ->
            CallConfigManager.setWxaModelId(this, text?.toString() ?: "")
        }
    }

    private fun setupContactsList() {
        // 加载通讯录
        contacts.clear()
        contacts.addAll(CallConfigManager.getContacts(this))

        contactAdapter = ContactAdapter(
            contacts = contacts,
            onEditClick = { index, contact -> showEditContactDialog(index, contact) },
            onDeleteClick = { index, contact -> showDeleteConfirmDialog(index, contact) }
        )

        binding.rvContacts.apply {
            layoutManager = LinearLayoutManager(this@CallConfigActivity)
            adapter = contactAdapter
        }

        binding.btnAddContact.setOnClickListener {
            showAddContactDialog()
        }

        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (contacts.isEmpty()) {
            binding.tvEmptyContacts.visibility = View.VISIBLE
            binding.rvContacts.visibility = View.GONE
        } else {
            binding.tvEmptyContacts.visibility = View.GONE
            binding.rvContacts.visibility = View.VISIBLE
        }
    }

    private fun showAddContactDialog() {
        val dialogBinding = DialogContactEditBinding.inflate(layoutInflater)

        AlertDialog.Builder(this)
            .setTitle(R.string.add_contact)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val nickname = dialogBinding.etNickname.text?.toString()?.trim() ?: ""
                val openId = dialogBinding.etOpenId.text?.toString()?.trim() ?: ""

                if (nickname.isNotEmpty() && openId.isNotEmpty()) {
                    val contact = Contact(nickname, openId)
                    CallConfigManager.addContact(this, contact)
                    contacts.add(contact)
                    contactAdapter.notifyItemInserted(contacts.size - 1)
                    updateEmptyState()
                    showToast(getString(R.string.contact_added))
                } else {
                    showToast(getString(R.string.please_fill_all_fields))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditContactDialog(index: Int, contact: Contact) {
        val dialogBinding = DialogContactEditBinding.inflate(layoutInflater)
        dialogBinding.etNickname.setText(contact.nickname)
        dialogBinding.etOpenId.setText(contact.openId)

        AlertDialog.Builder(this)
            .setTitle(R.string.edit_contact)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val nickname = dialogBinding.etNickname.text?.toString()?.trim() ?: ""
                val openId = dialogBinding.etOpenId.text?.toString()?.trim() ?: ""

                if (nickname.isNotEmpty() && openId.isNotEmpty()) {
                    val updatedContact = Contact(nickname, openId)
                    CallConfigManager.updateContact(this, index, updatedContact)
                    contacts[index] = updatedContact
                    contactAdapter.notifyItemChanged(index)
                    showToast(getString(R.string.contact_updated))
                } else {
                    showToast(getString(R.string.please_fill_all_fields))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteConfirmDialog(index: Int, contact: Contact) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_contact)
            .setMessage(getString(R.string.delete_contact_confirm, contact.nickname))
            .setPositiveButton(R.string.confirm) { _, _ ->
                CallConfigManager.removeContact(this, index)
                contacts.removeAt(index)
                contactAdapter.notifyItemRemoved(index)
                updateEmptyState()
                showToast(getString(R.string.contact_deleted))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
