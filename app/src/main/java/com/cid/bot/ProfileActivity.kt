package com.cid.bot

import android.app.Activity
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_profile.*

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        bTchangePassword.setOnClickListener {
            val layout = layoutInflater.inflate(R.layout.dialog_change_password, null)

            val dialog = AlertDialog.Builder(this)
                    .setTitle("Change Password")
                    .setMessage("Please input your current password and new password")
                    .setView(layout)
                    .setPositiveButton("Change", null)
                    .setNegativeButton("Cancel", null)
                    .show()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { _ ->
                val currentPassword = layout.findViewById<EditText>(R.id.eTcurrentPassword).text.toString()
                val newPassword = layout.findViewById<EditText>(R.id.eTnewPassword).text.toString()
                val newPasswordConfirm = layout.findViewById<EditText>(R.id.eTnewPasswordConfirm).text.toString()
                if (newPassword != newPasswordConfirm) {
                    Toast.makeText(this, "New Passwords are not identical.", Toast.LENGTH_SHORT).show()
                } else {
                    tryChangePassword(currentPassword, newPassword)
                    dialog.dismiss()
                }
            }
        }

        bTwithdraw.setOnClickListener {
            val layout = layoutInflater.inflate(R.layout.dialog_withdraw, null)

            AlertDialog.Builder(this)
                    .setTitle("Withdraw")
                    .setMessage("Please input your username and password.")
                    .setView(layout)
                    .setPositiveButton("Withdraw") { _, _ ->
                        val username = layout.findViewById<EditText>(R.id.eTusername).text.toString()
                        val password = layout.findViewById<EditText>(R.id.eTpassword).text.toString()
                        tryWithdraw(username, password)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
        }
    }

    private var changePasswordTask: Disposable? = null
    private fun tryChangePassword(oldPassword: String, newPassword: String) {
        if (changePasswordTask != null) return

        changePasswordTask = NetworkManager.call(API.changePassword(oldPassword, newPassword), {
            Toast.makeText(this, "Your password has been changed successfully.", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }, {
            Toast.makeText(this, if ("error" in it) it["error"] else "Changing password did not finish successfully. Please try again.", Toast.LENGTH_SHORT).show()
        }, {
            changePasswordTask = null
        })
    }

    private var withdrawTask: Disposable? = null
    private fun tryWithdraw(username: String, password: String) {
        if (withdrawTask != null) return

        withdrawTask = NetworkManager.call(API.withdraw(username, password), {
            Toast.makeText(this, "Your membership has been removed successfully.", Toast.LENGTH_SHORT).show()
            setResult(RESULT_CANCELED)
            finish()
        }, {
            Toast.makeText(this, "Withdrawal did not finish successfully. Please try again.", Toast.LENGTH_SHORT).show()
        }, {
            withdrawTask = null
        })
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_profile, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.mIsave -> {
                // TODO: save user profile
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        changePasswordTask?.dispose()
        withdrawTask?.dispose()
        super.onDestroy()
    }
}