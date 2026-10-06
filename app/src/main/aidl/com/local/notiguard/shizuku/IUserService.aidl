package com.local.notiguard.shizuku;

interface IUserService {
    /** Called by Shizuku when the service is destroyed. */
    void destroy() = 16777114; // Destroy ID from Shizuku

    /** Run a shell command and return "exit\n<combined stdout+stderr>". */
    String exec(String command) = 1;
}
