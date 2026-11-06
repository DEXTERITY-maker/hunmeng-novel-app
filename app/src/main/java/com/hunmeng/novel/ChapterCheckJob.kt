package com.hunmeng.novel

import android.app.job.JobParameters
import android.app.job.JobService

/** Фоновая задача: системный планировщик запускает её раз в несколько часов. */
class ChapterCheckJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            val ok = try { ChapterChecker.run(applicationContext) } catch (e: Exception) { false }
            jobFinished(params, !ok) // не получилось — система повторит позже
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
