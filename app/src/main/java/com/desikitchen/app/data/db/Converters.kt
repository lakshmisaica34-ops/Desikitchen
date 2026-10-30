package com.desikitchen.app.data.db

import androidx.room.TypeConverter
import com.desikitchen.app.data.model.MealSlot

class Converters {
    @TypeConverter
    fun slotToString(slot: MealSlot): String = slot.name

    @TypeConverter
    fun stringToSlot(value: String): MealSlot = MealSlot.valueOf(value)
}
