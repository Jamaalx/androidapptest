package com.example.email2whatsapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.email2whatsapp.databinding.ItemLogBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter(private val logs: List<LogManager.LogEntry>) : 
    RecyclerView.Adapter<LogAdapter.LogViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return LogViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logs[position])
    }
    
    override fun getItemCount(): Int = logs.size
    
    class LogViewHolder(private val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(log: LogManager.LogEntry) {
            binding.logTimeText.text = log.getFormattedTime()
            binding.logMessageText.text = log.message
        }
    }
}
