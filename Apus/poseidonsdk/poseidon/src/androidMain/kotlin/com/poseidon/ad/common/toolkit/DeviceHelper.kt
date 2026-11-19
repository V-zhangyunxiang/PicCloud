@file:JvmName("SizeUtil")

package com.poseidon.ad.common.toolkit

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import androidx.annotation.Dimension
import kotlin.math.sqrt

/**
 * 设备尺寸相关工具，负责 dp/px 转换、屏幕宽度、设备类型判断等基础能力。
 */
object DeviceHelper {

    private const val TAG = "DeviceHelper"
    private const val TABLET_MIN_DIAGONAL_INCH = 7.0

    /**
     * dp 转 px，使用系统 DisplayMetrics 进行换算。
     */
    @JvmStatic
    @Dimension(unit = Dimension.PX)
    fun dp2px(context: Context, @Dimension(unit = Dimension.DP) value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics)
    }

    /**
     * 获取屏幕宽度（像素单位）。
     */
    @JvmStatic
    @Dimension(unit = Dimension.PX)
    fun screenWidth(context: Context): Int {
        return context.resources.displayMetrics.widthPixels
    }

    /**
     * 设备类型：平板或手机。
     */
    internal enum class DeviceType {
        Tablet,
        Phone
    }

    /**
     * 根据屏幕布局和物理尺寸判断设备类型。
     */
    internal fun getDeviceType(context: Context): DeviceType {
        val metrics = DeviceMetrics.from(context)
        val tabletLike = metrics.isLargeLayout() && metrics.hasDiagonalAtLeast(TABLET_MIN_DIAGONAL_INCH)
        return if (tabletLike) DeviceType.Tablet else DeviceType.Phone
    }

    /**
     * 采集的屏幕信息快照，避免重复读取 DisplayMetrics。
     */
    private data class DeviceMetrics(
        val widthPixels: Int,
        val heightPixels: Int,
        val xdpi: Float,
        val ydpi: Float,
        val configuration: Configuration
    ) {

        fun isLargeLayout(): Boolean {
            val mask = configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
            return mask >= Configuration.SCREENLAYOUT_SIZE_LARGE
        }

        fun hasDiagonalAtLeast(minInch: Double): Boolean {
            return try {
                val xInch = widthPixels / xdpi
                val yInch = heightPixels / ydpi
                val diagonal = sqrt((xInch * xInch + yInch * yInch).toDouble())
                diagonal >= minInch
            } catch (error: Throwable) {
                logWarn(error, TAG) { "Failed to estimate diagonal size, fallback to phone" }
                false
            }
        }

        companion object {
            fun from(context: Context): DeviceMetrics {
                val dm = context.resources.displayMetrics
                return DeviceMetrics(
                    widthPixels = dm.widthPixels,
                    heightPixels = dm.heightPixels,
                    xdpi = dm.xdpi,
                    ydpi = dm.ydpi,
                    configuration = context.resources.configuration
                )
            }
        }
    }
}

/**
 * 旧版 API 的包装函数，便于历史代码调用。
 */
@Dimension(unit = Dimension.PX)
fun dp2px(context: Context, @Dimension(unit = Dimension.DP) value: Float): Float =
    DeviceHelper.dp2px(context, value)

/**
 * 旧版屏幕宽度查询入口。
 */
@Dimension(unit = Dimension.PX)
internal fun screenWidth(context: Context): Int = DeviceHelper.screenWidth(context)
