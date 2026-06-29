package com.profitdriving

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtils {

    private val dateTimeShort = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    private val timeOnly = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val fullDate = SimpleDateFormat("dd 'de' MMMM, yyyy", Locale("pt", "BR"))
    private val dayMonth = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
    private val fullMonth = SimpleDateFormat("MMMM, yyyy", Locale("pt", "BR"))
    private val dateShort = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
    private val dateTimeFull = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    fun dateTimeShort(timestamp: Long): String = dateTimeShort.format(Date(timestamp))
    fun dateTimeShort(date: Date): String = dateTimeShort.format(date)

    fun timeOnly(timestamp: Long): String = timeOnly.format(Date(timestamp))
    fun timeOnly(date: Date): String = timeOnly.format(date)

    fun isoDate(timestamp: Long): String = isoDate.format(Date(timestamp))
    fun isoDate(date: Date): String = isoDate.format(date)

    fun fullDate(timestamp: Long): String = fullDate.format(Date(timestamp))
    fun fullDate(date: Date): String = fullDate.format(date)

    fun dayMonth(timestamp: Long): String = dayMonth.format(Date(timestamp))
    fun dayMonth(date: Date): String = dayMonth.format(date)

    fun fullMonth(timestamp: Long): String = fullMonth.format(Date(timestamp))
    fun fullMonth(date: Date): String = fullMonth.format(date)

    fun dateShort(timestamp: Long): String = dateShort.format(Date(timestamp))
    fun dateShort(date: Date): String = dateShort.format(date)

    fun dateTimeFull(timestamp: Long): String = dateTimeFull.format(Date(timestamp))
    fun dateTimeFull(date: Date): String = dateTimeFull.format(date)
}
