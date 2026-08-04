<#
.SYNOPSIS
    Verifies a CareerPilot AI deployment end to end.

.DESCRIPTION
    Exercises the full authentication surface against a running instance and
    reports pass/fail per check. Exits non-zero if anything fails, so it can be
    used as a deployment gate in CI.

    This is deliberately more than a health check. A service can return
    {"status":"UP"} while the schema is half-migrated, Swagger is exposed in
    production, or the security chain is letting anonymous requests through.
    The checks below are the ones that would be embarrassing to discover later.

    The rotation checks are the important ones: they prove refresh-token reuse
    detection survived deployment. That mechanism fails silently — when it stops
    working, logins still succeed and nobody notices until a stolen token is
    used. Only an explicit test tells you.

    Every run registers a fresh account with a random address, so it is safe to
    run repeatedly against the same environment.

    Compatible with Windows PowerShell 5.1 and PowerShell 7+.

.PARAMETER BaseUrl
    Root URL of the deployment, no trailing slash.

.EXAMPLE
    .\scripts\smoke-test.ps1 -BaseUrl "https://careerpilot-api.up.railway.app"

.EXAMPLE
    .\scripts\smoke-test.ps1 -BaseUrl "http://localhost:8080"
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')

# Windows PowerShell 5.1 runs on .NET Framework, whose default protocol set can
# exclude TLS 1.2. Railway serves TLS 1.2+ only, so without this every HTTPS
# call fails with "The request was aborted: Could not create SSL/TLS secure
# channel" - an error that looks like a certificate problem and is not.
try {
    [Net.ServicePointManager]::SecurityProtocol =
        [Net.SecurityProtocolType]::Tls12 -bor [Net.SecurityProtocolType]::Tls11
} catch {
    # PowerShell 7 on .NET 6+ negotiates TLS itself and may not expose this.
}

$script:Passed = 0
$script:Failed = 0

function Write-Result {
    param([bool]$Ok, [string]$Name, [string]$Detail = '')

    if ($Ok) {
        $script:Passed++
        Write-Host "  PASS  " -ForegroundColor Green -NoNewline
    } else {
        $script:Failed++
        Write-Host "  FAIL  " -ForegroundColor Red -NoNewline
    }
    Write-Host $Name -NoNewline
    if ($Detail) { Write-Host "  ($Detail)" -ForegroundColor DarkGray } else { Write-Host "" }
}

<#
    Performs one API call and returns status, parsed body, raw body, and headers.

    Several checks below EXPECT a 4xx, so a non-2xx must be a value to assert on
    rather than an exception to escape. Invoke-WebRequest throws on any non-2xx
    by default, and the two PowerShell editions expose the failed response
    differently:

      * 5.1 (.NET Framework) - $_.Exception.Response is an HttpWebResponse;
        the body must be read from its response stream.
      * 7+  (.NET Core)      - $_.Exception.Response is an HttpResponseMessage;
        the body is already captured in $_.ErrorDetails.Message.

    -SkipHttpErrorCheck would remove the need for all of this, but it does not
    exist in 5.1. Handling both keeps one script for both editions.
#>
function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body,
        [string]$BearerToken
    )

    $headers = @{ 'Content-Type' = 'application/json' }
    if ($BearerToken) { $headers['Authorization'] = "Bearer $BearerToken" }

    $params = @{
        Uri             = "$BaseUrl$Path"
        Method          = $Method
        Headers         = $headers
        TimeoutSec      = 60
        UseBasicParsing = $true   # required on 5.1; harmless on 7+
    }
    if ($null -ne $Body) {
        $params['Body'] = ($Body | ConvertTo-Json -Depth 5 -Compress)
    }

    $status = 0
    $raw = ''
    $responseHeaders = @{}

    try {
        $response = Invoke-WebRequest @params
        $status = [int]$response.StatusCode
        $raw = $response.Content
        $responseHeaders = $response.Headers
    } catch {
        $ex = $_.Exception

        if ($ex.PSObject.Properties.Name -contains 'Response' -and $null -ne $ex.Response) {
            try { $status = [int]$ex.Response.StatusCode } catch { $status = 0 }

            try { $responseHeaders = $ex.Response.Headers } catch { $responseHeaders = @{} }

            # PowerShell 7 path: the body is already here.
            if ($null -ne $_.ErrorDetails -and $_.ErrorDetails.Message) {
                $raw = $_.ErrorDetails.Message
            }
            # PowerShell 5.1 path: read it off the stream.
            elseif ($ex.Response.PSObject.Methods.Name -contains 'GetResponseStream') {
                try {
                    $stream = $ex.Response.GetResponseStream()
                    $reader = New-Object System.IO.StreamReader($stream)
                    $raw = $reader.ReadToEnd()
                    $reader.Dispose()
                } catch { $raw = '' }
            }
        } else {
            # No response at all: DNS failure, connection refused, TLS failure.
            Write-Host "  ....  network error calling $Path : $($ex.Message)" -ForegroundColor DarkYellow
        }
    }

    $parsed = $null
    if ($raw) { try { $parsed = $raw | ConvertFrom-Json } catch { $parsed = $null } }

    return [pscustomobject]@{
        Status  = $status
        Body    = $parsed
        Raw     = [string]$raw
        Headers = $responseHeaders
    }
}

# Header access differs between editions: 5.1 yields a string, 7 yields a
# string[]. Normalise so the assertion does not have to care.
function Get-HeaderValue {
    param($Headers, [string]$Name)
    if ($null -eq $Headers) { return $null }
    foreach ($key in $Headers.Keys) {
        if ($key -ieq $Name) {
            $value = $Headers[$key]
            if ($value -is [array]) { return $value[0] }
            return $value
        }
    }
    return $null
}

# Reads a property that may be absent, without tripping Set-StrictMode.
function Get-Field {
    param($Object, [string]$Path)
    $current = $Object
    foreach ($segment in $Path.Split('.')) {
        if ($null -eq $current) { return $null }
        if ($current.PSObject.Properties.Name -notcontains $segment) { return $null }
        $current = $current.$segment
    }
    return $current
}

Write-Host ""
Write-Host "CareerPilot AI - deployment smoke test" -ForegroundColor Cyan
Write-Host "Target: $BaseUrl"
Write-Host ""

# ---------------------------------------------------------------------------
Write-Host "Health" -ForegroundColor Yellow

$health = Invoke-Api -Method GET -Path '/actuator/health'
Write-Result ($health.Status -eq 200 -and (Get-Field $health.Body 'status') -eq 'UP') `
    "health endpoint reports UP" "HTTP $($health.Status)"

if ($health.Status -eq 0) {
    Write-Host ""
    Write-Host "The service is unreachable. Nothing else can be checked." -ForegroundColor Red
    Write-Host "Check the URL, and that the Railway deployment is running." -ForegroundColor Yellow
    exit 1
}

$readiness = Invoke-Api -Method GET -Path '/actuator/health/readiness'
Write-Result ($readiness.Status -eq 200) `
    "readiness probe passes (database reachable)" "HTTP $($readiness.Status)"

# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "Registration" -ForegroundColor Yellow

$email = "smoke-$([guid]::NewGuid())@example.com"
$password = "a-long-enough-password"

$register = Invoke-Api -Method POST -Path '/api/v1/auth/register' -Body @{
    email = $email; password = $password; fullName = 'Smoke Test'
}
Write-Result ($register.Status -eq 201) "registration returns 201" "HTTP $($register.Status)"

$roles = Get-Field $register.Body 'data.roles'
Write-Result ($null -ne $roles -and ($roles -contains 'ROLE_USER')) "ROLE_USER granted at registration"

# NFR-SEC-05 verified on the wire. A field added to the User entity in a later
# phase cannot start leaking without failing this line.
$leaks = @('passwordHash', 'failedLoginAttempts', 'aiCreditsUsedMonth', 'lockedUntil')
$leaked = @($leaks | Where-Object { $register.Raw -match $_ })
Write-Result ($leaked.Count -eq 0) "response leaks no internal fields" `
    $(if ($leaked.Count) { "leaked: $($leaked -join ', ')" } else { "" })

$duplicate = Invoke-Api -Method POST -Path '/api/v1/auth/register' -Body @{
    email = $email; password = $password; fullName = 'Smoke Test'
}
Write-Result ($duplicate.Status -eq 409) "duplicate address rejected with 409" "HTTP $($duplicate.Status)"

$badPayload = Invoke-Api -Method POST -Path '/api/v1/auth/register' -Body @{
    email = 'not-an-email'; password = 'short'; fullName = 'X'
}
Write-Result ($badPayload.Status -eq 422) "invalid payload rejected with 422" "HTTP $($badPayload.Status)"

# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "Login" -ForegroundColor Yellow

$login = Invoke-Api -Method POST -Path '/api/v1/auth/login' -Body @{
    email = $email; password = $password
}
Write-Result ($login.Status -eq 200) "login returns 200" "HTTP $($login.Status)"

$accessToken = Get-Field $login.Body 'data.accessToken'
$refreshToken = Get-Field $login.Body 'data.refreshToken'
Write-Result ([bool]$accessToken) "access token issued"
Write-Result ([bool]$refreshToken) "refresh token issued"

$wrongPassword = Invoke-Api -Method POST -Path '/api/v1/auth/login' -Body @{
    email = $email; password = 'definitely-the-wrong-password'
}
Write-Result ($wrongPassword.Status -eq 401) "wrong password rejected with 401" "HTTP $($wrongPassword.Status)"

# The two failure modes must be indistinguishable, or the difference between
# them becomes a user-enumeration oracle.
$unknownUser = Invoke-Api -Method POST -Path '/api/v1/auth/login' -Body @{
    email = "nobody-$([guid]::NewGuid())@example.com"; password = 'definitely-the-wrong-password'
}
Write-Result ($unknownUser.Status -eq $wrongPassword.Status -and
              (Get-Field $unknownUser.Body 'data.code') -eq (Get-Field $wrongPassword.Body 'data.code')) `
    "unknown address is indistinguishable from wrong password"

# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "Authorization" -ForegroundColor Yellow

$anonymous = Invoke-Api -Method GET -Path '/api/v1/auth/me'
Write-Result ($anonymous.Status -eq 401) "anonymous request rejected with 401" "HTTP $($anonymous.Status)"
Write-Result ((Get-Field $anonymous.Body 'data.code') -eq 'UNAUTHORIZED') "401 uses the standard error envelope"

$forged = Invoke-Api -Method GET -Path '/api/v1/auth/me' -BearerToken 'not.a.real.token'
Write-Result ($forged.Status -eq 401) "forged token rejected with 401" "HTTP $($forged.Status)"

$me = Invoke-Api -Method GET -Path '/api/v1/auth/me' -BearerToken $accessToken
Write-Result ($me.Status -eq 200 -and (Get-Field $me.Body 'data.email') -eq $email) `
    "valid token returns the current user" "HTTP $($me.Status)"

# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "Observability" -ForegroundColor Yellow

$correlationId = Get-HeaderValue $anonymous.Headers 'X-Correlation-Id'
Write-Result ([bool]$correlationId) "failed responses carry a correlation id (NFR-OBS-01)"

# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "Refresh token rotation" -ForegroundColor Yellow

$refreshed = Invoke-Api -Method POST -Path '/api/v1/auth/refresh' -Body @{ refreshToken = $refreshToken }
Write-Result ($refreshed.Status -eq 200) "refresh returns 200" "HTTP $($refreshed.Status)"

$rotatedToken = Get-Field $refreshed.Body 'data.refreshToken'
Write-Result ($rotatedToken -and $rotatedToken -ne $refreshToken) `
    "a different refresh token is issued (rotation)"

# --- the checks that matter -------------------------------------------------
# Replay the token that was already consumed. This is what a stolen token looks
# like. Both the replayed token AND the victim's current one must die.
$replay = Invoke-Api -Method POST -Path '/api/v1/auth/refresh' -Body @{ refreshToken = $refreshToken }
Write-Result ($replay.Status -eq 401 -and (Get-Field $replay.Body 'data.code') -eq 'TOKEN_REUSE_DETECTED') `
    "replaying a consumed token is detected as theft" "code: $(Get-Field $replay.Body 'data.code')"

$victimToken = Invoke-Api -Method POST -Path '/api/v1/auth/refresh' -Body @{ refreshToken = $rotatedToken }
Write-Result ($victimToken.Status -eq 401) `
    "* the whole token family is revoked, not just the replayed token" "HTTP $($victimToken.Status)"

# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "Enumeration resistance" -ForegroundColor Yellow

$forgotUnknown = Invoke-Api -Method POST -Path '/api/v1/auth/forgot-password' -Body @{
    email = "nobody-$([guid]::NewGuid())@example.com"
}
$forgotKnown = Invoke-Api -Method POST -Path '/api/v1/auth/forgot-password' -Body @{ email = $email }
Write-Result ($forgotUnknown.Status -eq 202 -and $forgotKnown.Status -eq 202) `
    "forgot-password answers identically for known and unknown addresses"

# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "Production hardening" -ForegroundColor Yellow

$swagger = Invoke-Api -Method GET -Path '/v3/api-docs'
Write-Result ($swagger.Status -ne 200) "OpenAPI document is not public" `
    $(if ($swagger.Status -eq 200) { "EXPOSED - is SPRING_PROFILES_ACTIVE=prod set?" } else { "HTTP $($swagger.Status)" })

$envEndpoint = Invoke-Api -Method GET -Path '/actuator/env'
Write-Result ($envEndpoint.Status -ne 200) "/actuator/env is not public" "HTTP $($envEndpoint.Status)"

# ---------------------------------------------------------------------------
Write-Host ""
Write-Host ("-" * 60)
if ($script:Failed -eq 0) {
    Write-Host "$($script:Passed) passed, 0 failed." -ForegroundColor Green
    Write-Host "Deployment verified." -ForegroundColor Green
    exit 0
} else {
    Write-Host "$($script:Passed) passed, $($script:Failed) FAILED." -ForegroundColor Red
    Write-Host "See docs/phase-04-deployment/01-railway-deployment.md - Troubleshooting." -ForegroundColor Yellow
    exit 1
}
