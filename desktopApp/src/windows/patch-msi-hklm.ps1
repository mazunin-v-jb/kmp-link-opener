# Patches HKLM browser-registration entries into a jpackage-produced MSI.
#
# jpackage (JDK 21) generates WiX XML programmatically and ignores files
# placed in --resource-dir, so the only reliable way to add registry entries
# is to modify the MSI database directly after the fact via the Windows
# Installer COM API.
#
# Adds three Components to HKLM:
#   HklmProgId       - SOFTWARE\Classes\LinkOpener.URL (ProgId)
#   HklmCapabilities - SOFTWARE\Clients\StartMenuInternet\LinkOpener
#                      + SOFTWARE\RegisteredApplications
#   HklmAppPaths     - SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths
#                      (required for Windows 11 UserChoice hash validation)
#
# Idempotent: exits 0 without changes if HklmProgId component already present.
#
# Usage (called by Gradle task patchMsiHklm):
#   powershell -File patch-msi-hklm.ps1 -MsiPath "C:\...\Link Opener-x.y.z.msi"

param([Parameter(Mandatory)][string]$MsiPath)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Test-Path $MsiPath)) {
    Write-Error "MSI not found: $MsiPath"
    exit 1
}

Write-Host "Patching HKLM browser registration into: $MsiPath"

$installer = New-Object -ComObject WindowsInstaller.Installer
$db = $installer.OpenDatabase($MsiPath, 1)

# Idempotency check
$checkView = $db.OpenView("SELECT Component FROM Component WHERE Component = 'HklmProgId'")
$checkView.Execute()
$existing = $checkView.Fetch()
$checkView.Close()
if ($null -ne $existing) {
    Write-Host "Already patched -- skipping."
    exit 0
}

# 1. Components
# Attributes = 260 (4=RegistryKeyPath + 256=64bit), matching the existing
# registry components that jpackage writes into this MSI.
$compSql = "INSERT INTO Component (Component, ComponentId, Directory_, Attributes, Condition, KeyPath) VALUES (?, ?, ?, ?, ?, ?)"

$components = @(
    @("HklmProgId",       "{A3B2C1D0-E4F5-6A7B-8C9D-0E1F2A3B4C5D}", "regProgIdKey"),
    @("HklmCapabilities", "{5F6E7D8C-9B0A-1C2D-3E4F-5A6B7C8D9E0F}", "regCapKey"),
    @("HklmAppPaths",     "{9E8D7C6B-5A4B-3C2D-1E0F-9A8B7C6D5E4F}", "regAppPathsKey")
)

foreach ($c in $components) {
    $view = $db.OpenView($compSql)
    $rec = $installer.CreateRecord(6)
    $rec.StringData(1) = $c[0]
    $rec.StringData(2) = $c[1]
    $rec.StringData(3) = "TARGETDIR"
    $rec.IntegerData(4) = 260
    # field 5 (Condition) stays NULL
    $rec.StringData(6) = $c[2]
    $view.Execute($rec)
    $view.Close()
}

# 2. Registry entries
# Root 2 = HKLM.  Name=$null means the default (unnamed) value of the key.
# [INSTALLDIR] is an MSI property expanded to the install directory at runtime.
# "#1" is MSI syntax for REG_DWORD value 1.
# `Key` is a reserved word in MSI SQL and must be backtick-quoted.
$regSql = 'INSERT INTO Registry (Registry, Root, `Key`, Name, Value, Component_) VALUES (?, ?, ?, ?, ?, ?)'

$entries = @(
    @{ Id="regProgIdKey";    Root=2; Key="SOFTWARE\Classes\LinkOpener.URL";                                          Name=$null;                  Val="Link Opener URL Handler";                              Comp="HklmProgId" },
    @{ Id="regProgIdIcon";   Root=2; Key="SOFTWARE\Classes\LinkOpener.URL\DefaultIcon";                              Name=$null;                  Val="[INSTALLDIR]Link Opener.exe,0";                        Comp="HklmProgId" },
    @{ Id="regProgIdUMId";   Root=2; Key="SOFTWARE\Classes\LinkOpener.URL\Application";                              Name="AppUserModelId";        Val="LinkOpener";                                           Comp="HklmProgId" },
    @{ Id="regProgIdAppNm";  Root=2; Key="SOFTWARE\Classes\LinkOpener.URL\Application";                              Name="ApplicationName";       Val="Link Opener";                                          Comp="HklmProgId" },
    @{ Id="regProgIdDesc";   Root=2; Key="SOFTWARE\Classes\LinkOpener.URL\Application";                              Name="ApplicationDescription";Val="Pick which browser opens each link";                   Comp="HklmProgId" },
    @{ Id="regProgIdAppIco"; Root=2; Key="SOFTWARE\Classes\LinkOpener.URL\Application";                              Name="ApplicationIcon";       Val="[INSTALLDIR]Link Opener.exe,0";                        Comp="HklmProgId" },
    @{ Id="regProgIdCmd";    Root=2; Key="SOFTWARE\Classes\LinkOpener.URL\shell\open\command";                       Name=$null;                  Val='"[INSTALLDIR]Link Opener.exe" "%1"';                    Comp="HklmProgId" },
    @{ Id="regCapKey";       Root=2; Key="SOFTWARE\Clients\StartMenuInternet\LinkOpener";                            Name=$null;                  Val="Link Opener";                                          Comp="HklmCapabilities" },
    @{ Id="regCapIcon";      Root=2; Key="SOFTWARE\Clients\StartMenuInternet\LinkOpener\DefaultIcon";                Name=$null;                  Val="[INSTALLDIR]Link Opener.exe,0";                        Comp="HklmCapabilities" },
    @{ Id="regCapAppNm";     Root=2; Key="SOFTWARE\Clients\StartMenuInternet\LinkOpener\Capabilities";              Name="ApplicationName";       Val="Link Opener";                                          Comp="HklmCapabilities" },
    @{ Id="regCapDesc";      Root=2; Key="SOFTWARE\Clients\StartMenuInternet\LinkOpener\Capabilities";              Name="ApplicationDescription";Val="Pick which browser opens each link";                   Comp="HklmCapabilities" },
    @{ Id="regCapAppIco";    Root=2; Key="SOFTWARE\Clients\StartMenuInternet\LinkOpener\Capabilities";              Name="ApplicationIcon";       Val="[INSTALLDIR]Link Opener.exe,0";                        Comp="HklmCapabilities" },
    @{ Id="regCapHttp";      Root=2; Key="SOFTWARE\Clients\StartMenuInternet\LinkOpener\Capabilities\URLAssociations"; Name="http";               Val="LinkOpener.URL";                                       Comp="HklmCapabilities" },
    @{ Id="regCapHttps";     Root=2; Key="SOFTWARE\Clients\StartMenuInternet\LinkOpener\Capabilities\URLAssociations"; Name="https";              Val="LinkOpener.URL";                                       Comp="HklmCapabilities" },
    @{ Id="regCapIconsVis";  Root=2; Key="SOFTWARE\Clients\StartMenuInternet\LinkOpener\InstallInfo";               Name="IconsVisible";          Val="#1";                                                   Comp="HklmCapabilities" },
    @{ Id="regCapOpenCmd";   Root=2; Key="SOFTWARE\Clients\StartMenuInternet\LinkOpener\shell\open\command";        Name=$null;                  Val='"[INSTALLDIR]Link Opener.exe"';                         Comp="HklmCapabilities" },
    @{ Id="regRegApps";      Root=2; Key="SOFTWARE\RegisteredApplications";                                         Name="LinkOpener";            Val="Software\Clients\StartMenuInternet\LinkOpener\Capabilities"; Comp="HklmCapabilities" },
    @{ Id="regAppPathsKey";  Root=2; Key="SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\Link Opener.exe";     Name=$null;                  Val="[INSTALLDIR]Link Opener.exe";                           Comp="HklmAppPaths" },
    @{ Id="regAppPathsPath"; Root=2; Key="SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\Link Opener.exe";     Name="Path";                  Val="[INSTALLDIR]";                                          Comp="HklmAppPaths" }
)

foreach ($e in $entries) {
    $view = $db.OpenView($regSql)
    $rec = $installer.CreateRecord(6)
    $rec.StringData(1) = $e.Id
    $rec.IntegerData(2) = $e.Root
    $rec.StringData(3) = $e.Key
    if ($null -ne $e.Name) { $rec.StringData(4) = $e.Name }
    $rec.StringData(5) = $e.Val
    $rec.StringData(6) = $e.Comp
    $view.Execute($rec)
    $view.Close()
}

# 3. FeatureComponents -- link each new component to DefaultFeature
$fcSql = "INSERT INTO FeatureComponents (Feature_, Component_) VALUES (?, ?)"
foreach ($c in $components) {
    $view = $db.OpenView($fcSql)
    $rec = $installer.CreateRecord(2)
    $rec.StringData(1) = "DefaultFeature"
    $rec.StringData(2) = $c[0]
    $view.Execute($rec)
    $view.Close()
}

$db.Commit()
Write-Host "Done: HKLM browser registration entries added to MSI."
