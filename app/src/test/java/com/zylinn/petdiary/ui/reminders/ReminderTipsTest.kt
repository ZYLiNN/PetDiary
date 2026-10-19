package com.zylinn.petdiary.ui.reminders

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderTipsTest {
    // P4-08: wording follows the phone maker
    @Test fun brandSteps() {
        assertEquals(ReminderTips.Brand.COLOROS, ReminderTips.brand("OPPO"))
        assertEquals(ReminderTips.Brand.COLOROS, ReminderTips.brand("realme"))
        assertEquals(ReminderTips.Brand.ONE_UI, ReminderTips.brand("samsung"))
        assertEquals(ReminderTips.Brand.OTHER, ReminderTips.brand("Google"))
    }
}
