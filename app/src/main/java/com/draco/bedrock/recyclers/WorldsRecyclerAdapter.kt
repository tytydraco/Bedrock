package com.draco.bedrock.recyclers

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.draco.bedrock.R
import com.draco.bedrock.databinding.RecyclerWorldItemBinding
import com.draco.bedrock.models.WorldFile
import com.draco.bedrock.repositories.constants.WorldFileType
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.*

class WorldsRecyclerAdapter(
    private val context: Context,
    var worldFileList: MutableList<WorldFile>
) : RecyclerView.Adapter<WorldsRecyclerAdapter.ViewHolder>() {
    class ViewHolder(val binding: RecyclerWorldItemBinding): RecyclerView.ViewHolder(binding.root)

    private val accentColor: Int by lazy {
        val typedValue = TypedValue()
        val typedArray = context.obtainStyledAttributes(typedValue.data, intArrayOf(R.attr.colorSecondary))
        val color = typedArray.getColor(0, 0)
        typedArray.recycle()

        return@lazy color
    }

    private val defaultColor: Int by lazy {
        val typedValue = TypedValue()
        val typedArray = context.obtainStyledAttributes(typedValue.data, intArrayOf(R.attr.colorControlNormal))
        val color = typedArray.getColor(0, 0)
        typedArray.recycle()

        return@lazy color
    }

    /**
     * Create and show a dialog to confirm changes
     */
    private fun createConfirmDialog(messageResId: Int, action: () -> Unit) =
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.confirm_dialog_title)
            .setMessage(messageResId)
            .setPositiveButton(R.string.dialog_button_yes) { _, _ -> action() }
            .setNegativeButton(R.string.dialog_button_no) { _, _ -> }
            .show()

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

        holder.binding.name.text = worldFile.name

        when (worldFile.type) {
            WorldFileType.LOCAL -> {
                holder.binding.statusPhone.setColorFilter(accentColor)
                holder.binding.statusCloud.setColorFilter(defaultColor)
            }
            WorldFileType.REMOTE -> {
                holder.binding.statusPhone.setColorFilter(defaultColor)
                holder.binding.statusCloud.setColorFilter(accentColor)
            }
            WorldFileType.LOCAL_REMOTE -> {
                holder.binding.statusPhone.setColorFilter(accentColor)
                holder.binding.statusCloud.setColorFilter(accentColor)
            }
        }

        holder.binding.upload.setOnClickListener {
            createConfirmDialog(R.string.confirm_dialog_upload_message) {
                uploadHook?.invoke(worldFile.id)
            }
        }

        holder.binding.download.setOnClickListener {
            createConfirmDialog(R.string.desc_download) {
                downloadHook?.invoke(worldFile.id)
            }
        }

        holder.binding.deleteCloud.setOnClickListener {
            createConfirmDialog(R.string.confirm_dialog_delete_message) {
                deleteCloudHook?.invoke(worldFile.id)
            }
        }
    }
}