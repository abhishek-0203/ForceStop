package com.abhishek.forcestop.shizuku;

import android.os.Bundle;

interface IShellService {
    // Transaction code reserved by Shizuku for tearing down user services.
    void destroy() = 16777114;

    Bundle exec(String command) = 1;
}
