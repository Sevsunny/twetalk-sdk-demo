package com.tencent.twetalk_sdk_demo.call

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tencent.twetalk_sdk_demo.data.Contact
import com.tencent.twetalk_sdk_demo.databinding.ItemContactBinding

/**
 * 联系人列表适配器
 */
class ContactAdapter(
    private val contacts: MutableList<Contact>,
    private val onEditClick: (Int, Contact) -> Unit,
    private val onDeleteClick: (Int, Contact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(contacts[position], position)
    }

    override fun getItemCount(): Int = contacts.size

    fun updateContacts(newContacts: List<Contact>) {
        contacts.clear()
        contacts.addAll(newContacts)
        notifyDataSetChanged()
    }

    inner class ContactViewHolder(
        private val binding: ItemContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: Contact, position: Int) {
            binding.tvNickname.text = contact.nickname
            binding.tvOpenId.text = contact.openId

            binding.btnEdit.setOnClickListener {
                onEditClick(position, contact)
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick(position, contact)
            }
        }
    }
}
