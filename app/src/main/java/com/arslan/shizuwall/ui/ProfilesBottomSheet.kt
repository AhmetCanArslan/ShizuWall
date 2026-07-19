package com.arslan.shizuwall.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arslan.shizuwall.R
import com.arslan.shizuwall.adapters.ProfileAdapter
import com.arslan.shizuwall.model.Profile
import com.arslan.shizuwall.profiles.ProfilesStore
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import android.widget.PopupMenu
import android.widget.Toast


class ProfilesBottomSheet(
    private val context: Context,
    private val listener: Listener
) {

    interface Listener {
        fun onActivateProfile(profile: Profile)
    }

    private val dialog = BottomSheetDialog(context)
    private lateinit var adapter: ProfileAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.sheet_profiles, null)
        dialog.setContentView(view)

        recyclerView = view.findViewById(R.id.profilesRecyclerView)
        emptyState = view.findViewById(R.id.profilesEmptyState)
        val saveCurrent = view.findViewById<Button>(R.id.profileSaveCurrentButton)
        val autoEnableSwitch =
            view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.profileAutoEnableSwitch)

        val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        autoEnableSwitch.isChecked =
            prefs.getBoolean(MainActivity.KEY_AUTO_ENABLE_ON_PROFILE_ACTIVATE, false)
        autoEnableSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(MainActivity.KEY_AUTO_ENABLE_ON_PROFILE_ACTIVATE, isChecked).apply()
        }

        adapter = ProfileAdapter(
            onProfileClick = { profile -> activate(profile) },
            onMenuClick = { profile, anchor -> showItemMenu(profile, anchor) }
        )
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        saveCurrent.setOnClickListener { promptSaveCurrent() }

        refresh(playLayoutAnim = true)
        dialog.show()
    }

    fun dismiss() {
        if (dialog.isShowing) dialog.dismiss()
    }

    fun notifyActivated(profileId: String?) {
        if (::adapter.isInitialized) adapter.setActiveProfileId(profileId, animate = true)
    }

    private fun activate(profile: Profile) {
        if (ProfilesStore.activeProfileId(context) == profile.id) {
            Toast.makeText(context, context.getString(R.string.profile_already_active, profile.name), Toast.LENGTH_SHORT).show()
            return
        }
        adapter.setActiveProfileId(profile.id, animate = true)
        listener.onActivateProfile(profile)
    }

    private fun refresh(playLayoutAnim: Boolean) {
        val profiles = ProfilesStore.getProfiles(context)
        val activeId = ProfilesStore.activeProfileId(context)
        adapter.setActiveProfileId(activeId, animate = false)
        adapter.submitList(profiles)

        val isEmpty = profiles.isEmpty()
        emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE

        if (playLayoutAnim && !isEmpty) {
            recyclerView.layoutAnimation =
                AnimationUtils.loadLayoutAnimation(context, R.anim.profile_layout_anim)
            recyclerView.scheduleLayoutAnimation()
        }
    }

    private fun showItemMenu(profile: Profile, anchor: View) {
        val popup = PopupMenu(context, anchor)
        popup.menuInflater.inflate(R.menu.menu_profile_item, popup.menu)
        try {
            popup.setForceShowIcon(true)
        } catch (_: Throwable) {
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_profile_rename -> { promptRename(profile); true }
                R.id.action_profile_update -> { updateToCurrent(profile); true }
                R.id.action_profile_automation -> { showAutomationDialog(profile); true }
                R.id.action_profile_delete -> { confirmDelete(profile); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun promptSaveCurrent() {
        showNameDialog(
            title = context.getString(R.string.profile_save_current),
            initialName = "",
            positiveText = context.getString(R.string.profile_save)
        ) { name ->
            val current = ProfilesStore.captureCurrent(context)
            val profile = ProfilesStore.create(
                context = context,
                name = name,
                packages = current.packages,
                firewallMode = current.firewallMode,
                appModesJson = current.appModesJson,
                showSystemApps = current.showSystemApps
            )
            ProfilesStore.setActiveProfileId(context, profile.id)
            refresh(playLayoutAnim = true)
            Toast.makeText(context, context.getString(R.string.profile_saved, name), Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptRename(profile: Profile) {
        showNameDialog(
            title = context.getString(R.string.profile_rename),
            initialName = profile.name,
            positiveText = context.getString(R.string.profile_save)
        ) { name ->
            ProfilesStore.rename(context, profile.id, name)
            refresh(playLayoutAnim = false)
        }
    }

    private fun updateToCurrent(profile: Profile) {
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.profile_update_to_current))
            .setMessage(context.getString(R.string.profile_update_confirm, profile.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.profile_update_action) { _, _ ->
                val current = ProfilesStore.captureCurrent(context)
                ProfilesStore.update(
                    context,
                    profile.copy(
                        packages = current.packages,
                        firewallMode = current.firewallMode,
                        appModesJson = current.appModesJson,
                        showSystemApps = current.showSystemApps
                    )
                )
                ProfilesStore.setActiveProfileId(context, profile.id)
                refresh(playLayoutAnim = false)
                Toast.makeText(context, context.getString(R.string.profile_updated, profile.name), Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showAutomationDialog(profile: Profile) {
        val action = MainActivity.ACTION_PROFILE_CONTROL
        val component = "com.arslan.shizuwall/.receivers.ProfileControlReceiver"
        val extra = "${MainActivity.EXTRA_PROFILE_NAME}=${profile.name}"
        val adbCmd = "adb shell am broadcast -a $action -n $component " +
            "--es ${MainActivity.EXTRA_PROFILE_NAME} \"${profile.name}\""

        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        fun copy(label: String, value: String) {
            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText(label, value))
            Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }

        val desc = TextView(context).apply {
            text = context.getString(R.string.profile_automation_desc)
            textSize = 13f
            setPadding(0, 0, 0, dp(12))
        }
        container.addView(desc)

        fun addRow(labelText: String, value: String) {
            val label = TextView(context).apply {
                text = labelText
                textSize = 12f
                alpha = 0.7f
                setPadding(0, dp(8), 0, dp(2))
            }
            val valueView = TextView(context).apply {
                text = value
                textSize = 14f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextIsSelectable(true)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                val outValue = android.util.TypedValue()
                context.theme.resolveAttribute(
                    android.R.attr.selectableItemBackground, outValue, true
                )
                setBackgroundResource(outValue.resourceId)
                setOnClickListener { copy(labelText, value) }
            }
            container.addView(label)
            container.addView(valueView)
        }

        addRow(context.getString(R.string.adb_broadcast_field_action), action)
        addRow(context.getString(R.string.adb_broadcast_field_component), component)
        addRow(context.getString(R.string.adb_broadcast_field_extras), extra)
        addRow(context.getString(R.string.profile_automation_adb_label), adbCmd)

        val scroll = android.widget.ScrollView(context).apply { addView(container) }

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.profile_automation_title, profile.name))
            .setView(scroll)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun confirmDelete(profile: Profile) {
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.profile_delete))
            .setMessage(context.getString(R.string.profile_delete_confirm, profile.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.profile_delete) { _, _ ->
                ProfilesStore.delete(context, profile.id)
                refresh(playLayoutAnim = false)
            }
            .show()
    }

    private fun showNameDialog(
        title: String,
        initialName: String,
        positiveText: String,
        onConfirm: (String) -> Unit
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_profile_edit, null)
        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.profileNameInputLayout)
        val input = dialogView.findViewById<TextInputEditText>(R.id.profileNameInput)
        input.setText(initialName)
        input.setSelection(initialName.length)

        val alert = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(positiveText, null)
            .create()

        alert.setOnShowListener {
            input.requestFocus()
            alert.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    inputLayout.error = context.getString(R.string.profile_name_required)
                    return@setOnClickListener
                }
                onConfirm(name)
                alert.dismiss()
            }
        }
        alert.show()
    }

    companion object {
        @Suppress("unused")
        private const val TAG = "ProfilesBottomSheet"
    }
}
