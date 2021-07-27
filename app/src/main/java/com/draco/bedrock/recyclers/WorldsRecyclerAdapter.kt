package com.draco.bedrock.recyclers

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.draco.bedrock.databinding.RecyclerWorldItemBinding
import com.draco.bedrock.models.WorldFile

class WorldsRecyclerAdapter(
    private val context: Context,
    var worldFileList: MutableList<WorldFile>
) : RecyclerView.Adapter<WorldsRecyclerAdapter.ViewHolder>() {
    class ViewHolder(val binding: RecyclerWorldItemBinding): RecyclerView.ViewHolder(binding.root)

    override fun getItemCount() = worldFileList.size

    var uploadHook: ((worldName: String) -> Unit)? = null
    var downloadHook: ((worldName: String) -> Unit)? = null
    var deleteCloudHook: ((worldName: String) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RecyclerWorldItemBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val worldFile = worldFileList[position]
        holder.binding.name.text = "${worldFile.name} | ${worldFile.type}"

        holder.binding.upload.setOnClickListener {
            uploadHook?.invoke(worldFile.name)
        }

        holder.binding.download.setOnClickListener {
            downloadHook?.invoke(worldFile.name)
        }

        holder.binding.deleteCloud.setOnClickListener {
            deleteCloudHook?.invoke(worldFile.name)
        }
    }
}