package com.siy.mvvm.exm.http

@MustBeDocumented
@Target(
        AnnotationTarget.FIELD
)
@Retention(AnnotationRetention.RUNTIME)
annotation class ReqName(val reqName: String = "")