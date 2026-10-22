package com.jumlo.app.printing

import kotlin.math.max
import kotlin.math.min

/** Every value here uses PDF points (1/72 inch), in physical document coordinates, never RTL UI coordinates. */
data class PrintSize(val width:Double,val height:Double) {
    init { require(width.isFinite() && height.isFinite() && width>0 && height>0) { "PRINT_PAPER" } }
    companion object { fun mm(width:Double,height:Double)=PrintSize(width*72/25.4,height*72/25.4) }
}
data class PrintCrop(val x:Double,val y:Double,val width:Double,val height:Double,val unit:Double=1.0)
data class PrintMatrix(val a:Double,val b:Double,val c:Double,val d:Double,val e:Double,val f:Double) {
    fun map(x:Double,y:Double) = (a*x+c*y+e) to (b*x+d*y+f)
}
data class PrintPlacement(val x:Double,val y:Double,val scale:Double,val clipX:Double,val clipY:Double,val clipWidth:Double,val clipHeight:Double)
object PrintGeometry {
    fun rotation(degrees:Int):Int { require(degrees%90==0) { "PRINT_ROTATION" }; return ((degrees%360)+360)%360 }
    fun displaySize(crop:PrintCrop,degrees:Int):PrintSize {
        require(listOf(crop.x,crop.y,crop.width,crop.height,crop.unit).all {it.isFinite()} && crop.unit>0) { "PRINT_PAPER" }
        val size=PrintSize(crop.width*crop.unit,crop.height*crop.unit)
        return if(rotation(degrees)%180==0) size else PrintSize(size.height,size.width)
    }
    /** Raw cropped PDF coordinates -> zero-origin clockwise-rotated physical coordinates. */
    fun normalize(crop:PrintCrop,degrees:Int):PrintMatrix {
        displaySize(crop,degrees)
        val u=crop.unit; val x=crop.x; val y=crop.y; val w=crop.width; val h=crop.height
        return when(rotation(degrees)) {
            90 -> PrintMatrix(0.0,-u,u,0.0,-y*u,(w+x)*u)
            180 -> PrintMatrix(-u,0.0,0.0,-u,(w+x)*u,(h+y)*u)
            270 -> PrintMatrix(0.0,u,-u,0.0,(h+y)*u,-x*u)
            else -> PrintMatrix(u,0.0,0.0,u,-x*u,-y*u)
        }
    }
    fun place(source:PrintSize,paper:PrintSize,margins:PrintMargins,mode:PrintScale,percent:Int):PrintPlacement {
        margins.validate(); require(percent in 10..400) { "PRINT_SCALE" }
        val w=paper.width-margins.left-margins.right; val h=paper.height-margins.top-margins.bottom
        require(w>0 && h>0) { "PRINT_MARGINS" }
        val base=when(mode) { PrintScale.FIT->min(w/source.width,h/source.height); PrintScale.FILL->max(w/source.width,h/source.height); PrintScale.ACTUAL->1.0 }
        val scale=base*percent/100.0
        return PrintPlacement(margins.left+(w-source.width*scale)/2,margins.bottom+(h-source.height*scale)/2,scale,margins.left,margins.bottom,w,h)
    }
}
object PrintRanges {
    const val MAX_PAGES=10000
    fun sourcePages(ordered:List<PrintPage>,count:Int):List<PrintPage> {
        require(count in 1..MAX_PAGES && ordered.size<=MAX_PAGES) { "PRINT_PAGE_COUNT" }
        return (ordered.ifEmpty {(1..count).map {PrintPage(it)}}).map {
            require(it.sourcePage in 1..count) { "PRINT_RANGE" }; it.copy(rotationDegrees=PrintGeometry.rotation(it.rotationDegrees))
        }
    }
    /** Range numbers address the ordered edited document, including duplicate pages. */
    fun parse(text:String,count:Int):List<Int> {
        require(count in 1..MAX_PAGES && text.length<=2000) { "PRINT_RANGE" }
        val normalized=text.trim().map { character ->
            when(character) {
                in '٠'..'٩' -> '0'+(character-'٠')
                in '۰'..'۹' -> '0'+(character-'۰')
                '،' -> ','
                '–','—' -> '-'
                else -> character
            }
        }.joinToString("")
        if(normalized.isBlank() || normalized.equals("all",true) || normalized in setOf("الكل","كل الصفحات")) return (0 until count).toList()
        val selected=sortedSetOf<Int>()
        normalized.split(',').forEach { raw ->
            val match=Regex("([0-9]+)(?:\\s*-\\s*([0-9]+))?").matchEntire(raw.trim()) ?: errorRange()
            val first=match.groupValues[1].toIntOrNull() ?: errorRange()
            val last=match.groupValues[2].takeIf {it.isNotEmpty()}?.toIntOrNull() ?: if(match.groupValues[2].isEmpty()) first else errorRange()
            require(first in 1..count && last in first..count) { "PRINT_RANGE" }
            for(i in first..last) selected.add(i-1)
        }
        return selected.toList()
    }
    private fun errorRange():Nothing=throw IllegalArgumentException("PRINT_RANGE")
    fun select(pages:List<PrintPage>,text:String)=parse(text,pages.size).map { pages[it] }
    fun requested(ranges:List<IntRange>,count:Int):List<Int> {
        require(count in 1..MAX_PAGES) { "PRINT_PAGE_COUNT" }
        return (0 until count).filter { page -> ranges.any {page in it} }
    }
    fun compact(pages:List<Int>):List<IntRange> {
        val sorted=pages.distinct().sorted(); if(sorted.isEmpty()) return emptyList()
        val result=mutableListOf<IntRange>(); var first=sorted.first(); var last=first
        for(page in sorted.drop(1)) { if(page==last+1) last=page else {result+=first..last;first=page;last=page} }
        result+=first..last;return result
    }
}
