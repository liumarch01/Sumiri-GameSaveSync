package com.sumiri.gss

import android.app.Application
import androidx.annotation.Keep

class MainApplication : Application() {

    /**
     * 魅族 Flyme 专用的夜间模式豁免声明方法
     * Flyme 的 MzNightModeManager 会通过反射调用此方法：
     * 返回 2 代表应用已自适配深色模式，指示 Flyme 彻底禁用系统层的强制反色/二次刷色。
     */
    @Keep
    fun mzNightModeUseOf(): Int {
        return 2
    }
}
