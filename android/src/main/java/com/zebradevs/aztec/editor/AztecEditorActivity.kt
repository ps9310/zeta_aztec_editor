package com.zebradevs.aztec.editor

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat


class AztecEditorActivity : AppCompatActivity() {
    private var editText: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_aztec_editor)
        editText = findViewById(R.id.editText);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds the "Send" button to the action bar
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here.
        if (item.itemId == R.id.action_send) {
            // Get the text from the EditText
            val text: String = editText?.getText().toString()


            // Create an intent to hold the result data
            val resultIntent = Intent()
            resultIntent.putExtra("result", text)


            // Set the result and finish the activity
            setResult(RESULT_OK, resultIntent)
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val REQUEST_CODE: Int = 789

        fun createIntent(activity: Activity, initialHtml: String?): Intent {
            return Intent(activity, AztecEditorActivity::class.java).apply {
                putExtra("initialHtml", initialHtml)
            }
        }
    }
}