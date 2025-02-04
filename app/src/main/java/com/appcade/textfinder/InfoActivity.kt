package com.appcade.textfinder

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.fragment.app.FragmentActivity

class InfoActivity : FragmentActivity() {

    private lateinit var infoButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info)

        infoButton = findViewById(R.id.infoCloseButton)

        infoButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

    }
}