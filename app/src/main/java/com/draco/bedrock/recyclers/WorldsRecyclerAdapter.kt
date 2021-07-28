package com.draco.bedrock.recyclers

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.draco.bedrock.R
import com.draco.bedrock.databinding.RecyclerWorldItemBinding
import com.draco.bedrock.models.WorldFile
import com.draco.bedrock.repositories.constants.WorldFileType

class WorldsRecyclerAdapter(
    private val context: Context,
    var worldFileList: MutableList<WorldFile>
) : RecyclerView.Adapter<WorldsRecyclerAdapter.ViewHolder>() {
    private lateinit var recycler: RecyclerView

    var uploadHook: ((view: View, worldName: String) -> Unit)? = null
    var downloadHook: ((view: View, worldName: String) -> Unit)? = null
    var deleteDeviceHook: ((view: View, worldName: String) -> Unit)? = null
    var deleteCloudHook: ((view: View, worldName: String) -> Unit)? = null

    class ViewHolder(val binding: RecyclerWorldItemBinding): RecyclerView.ViewHolder(binding.root)

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recycler = recyclerView
    }

    /**
     * Get an attribute color
     * @param attrId Res-id for attribute
     * @return Color as an Int
     */
    private fun getColorAttr(attrId: Int): Int {
        val typedValue = TypedValue()
        val typedArray = context.obtainStyledAttributes(typedValue.data, intArrayOf(attrId))
        val color = typedArray.getColor(0, 0)
        typedArray.recycle()

        return color
    }

    private val accentColor by lazy { getColorAttr(R.attr.colorSecondary) }
    private val defaultColor by lazy { getColorAttr(R.attr.colorControlNormal) }

    override fun getItemCount() = worldFileList.size

    override fun getItemId(position: Int): Long {
        return worldFileList[position].id.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RecyclerWorldItemBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val worldFile = worldFileList[position]

        holder.binding.name.text = worldFile.name
        holder.binding.id.text = worldFile.id

        when (worldFile.type) {
            WorldFileType.LOCAL -> {
                holder.binding.statusPhone.setColorFilter(accentColor)
                holder.binding.statusCloud.setColorFilter(defaultColor)

                holder.binding.deleteDevice.isEnabled = true
                holder.binding.deleteCloud.isEnabled = false
                holder.binding.download.isEnabled = false
                holder.binding.upload.isEnabled = true
            }
            WorldFileType.REMOTE -> {
                holder.binding.statusPhone.setColorFilter(defaultColor)
                holder.binding.statusCloud.setColorFilter(accentColor)

                holder.binding.deleteDevice.isEnabled = false
                holder.binding.deleteCloud.isEnabled = true
                holder.binding.download.isEnabled = true
                holder.binding.upload.isEnabled = false
            }
            WorldFileType.LOCAL_REMOTE -> {
                holder.binding.statusPhone.setColorFilter(accentColor)
                holder.binding.statusCloud.setColorFilter(accentColor)

                holder.binding.deleteDevice.isEnabled = true
                holder.binding.deleteCloud.isEnabled = true
                holder.binding.download.isEnabled = true
                holder.binding.upload.isEnabled = true
            }
        }

        holder.binding.upload.setOnClickListener {
            uploadHook?.invoke(recycler, worldFile.id)
        }

        holder.binding.download.setOnClickListener {
            downloadHook?.invoke(recycler, worldFile.id)
        }

        holder.binding.deleteDevice.setOnClickListener {
            deleteDeviceHook?.invoke(recycler, worldFile.id)
        }

        holder.binding.deleteCloud.setOnClickListener {
            deleteCloudHook?.invoke(recycler, worldFile.id)
        }
    }
}