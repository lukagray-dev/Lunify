package com.android.lunify.auth

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

object GoogleSignInErrorMapper {
    
    fun getErrorMessage(exception: ApiException): String {
        return when (exception.statusCode) {
            CommonStatusCodes.NETWORK_ERROR -> 
                "Network error. Please check your internet connection and try again."
            CommonStatusCodes.TIMEOUT -> 
                "Sign in timed out. Please try again."
            CommonStatusCodes.CANCELED -> 
                "Sign in was canceled."
            CommonStatusCodes.DEVELOPER_ERROR -> 
                "Configuration error. Please contact support."
            CommonStatusCodes.INTERNAL_ERROR -> 
                "An internal error occurred. Please try again."
            CommonStatusCodes.INVALID_ACCOUNT -> 
                "Invalid account. Please select a valid Google account."
            CommonStatusCodes.SIGN_IN_REQUIRED -> 
                "Sign in required. Please try again."
            else -> 
                "Sign in failed (Error ${exception.statusCode}). Please try again."
        }
    }
}
