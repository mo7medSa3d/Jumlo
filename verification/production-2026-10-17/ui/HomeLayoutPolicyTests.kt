package com.jumlo.app.ui.policytests

import com.jumlo.app.ui.home.HomeActionPolicy
import com.jumlo.app.ui.adaptive.*

fun main() {
    var passed=0;var failed=0
    fun test(name:String,body:()->Unit) {try {body();println("PASS $name");passed++} catch(e:Throwable) {println("FAIL $name: ${e.message}");failed++}}
    fun same(expected:Any,actual:Any) {check(expected==actual) {"expected $expected; got $actual"}}
    val eligible=listOf("pdf_02","pdf_03","img_02","dl_02","sha256","img_04")
    test("user order survives, duplicates and ineligible IDs do not") {same(listOf("sha256","img_04","pdf_02"),HomeActionPolicy.resolve(listOf("sha256","hidden","sha256","file_02","img_04","pdf_02"),eligible))}
    test("empty or wholly disabled selection falls back to eligible defaults") {same(HomeActionPolicy.defaults,HomeActionPolicy.resolve(emptyList(),eligible));same(HomeActionPolicy.defaults,HomeActionPolicy.resolve(listOf("blocked"),eligible))}
    test("four slots maximum, replacement preserves chosen order") {same(listOf("sha256","img_04","dl_02","pdf_03"),HomeActionPolicy.resolve(listOf("sha256","img_04","dl_02","pdf_03","pdf_02"),eligible))}
    test("disabled defaults are never reintroduced") {same(listOf("pdf_03","dl_02"),HomeActionPolicy.resolve(emptyList(),listOf("pdf_03","dl_02")))}
    val root=PixelBounds(100,50,1100,850)
    test("no hinge preserves offset window root") {same(root,safeViewport(root,emptyList(),false))}
    test("vertical hinge coordinates account for offset root") {same(PixelBounds(100,50,650,850),safeViewport(root,listOf(HingeBounds(PixelBounds(650,0,680,1000),true)),false))}
    test("equal panes prefer reading start without mirrored coordinates") {same(PixelBounds(100,50,590,850),safeViewport(root,listOf(HingeBounds(PixelBounds(590,0,610,1000),true)),false));same(PixelBounds(610,50,1100,850),safeViewport(root,listOf(HingeBounds(PixelBounds(590,0,610,1000),true)),true))}
    test("zero width separating fold remains a boundary") {same(PixelBounds(600,50,1100,850),safeViewport(root,listOf(HingeBounds(PixelBounds(600,0,600,1000),true)),true))}
    test("horizontal fold chooses larger pane") {same(PixelBounds(100,220,1100,850),safeViewport(root,listOf(HingeBounds(PixelBounds(0,200,1500,220),false)),false))}
    test("offscreen hinge is ignored, overlapping edge hinge is clamped") {same(root,safeViewport(root,listOf(HingeBounds(PixelBounds(20,0,40,1000),true)),false));same(PixelBounds(120,50,1100,850),safeViewport(root,listOf(HingeBounds(PixelBounds(80,0,120,1000),true)),false))}
    test("multiple occlusions never cross the selected viewport") {same(PixelBounds(100,400,590,850),safeViewport(root,listOf(HingeBounds(PixelBounds(590,0,610,1000),true),HingeBounds(PixelBounds(0,350,1200,400),false)),false))}
    println("$passed passed; $failed failed");check(failed==0)
}
