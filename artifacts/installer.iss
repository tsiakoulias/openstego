;----------------------------------------------------------------------------------------
; Steganography utility to hide messages into cover files
; Copyright (c) Samir Vaidya (mailto:syvaidya@gmail.com)
;----------------------------------------------------------------------------------------

[Setup]
AppName=@AppName@
AppVersion=@AppVersion@
AppVerName=@AppName@ v@AppVersion@
AppPublisher=@AuthorName@
AppPublisherURL=@HomepageUrl@
WizardStyle=modern
SourceDir=@AppDir@
OutputDir=@DistDir@
DefaultDirName={autopf}\@AppName@
DefaultGroupName=@AppName@
OutputBaseFilename=Setup-@AppName@-@AppVersion@
LicenseFile=LICENSE
UninstallDisplayIcon={app}\@AppName@.exe
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog

[Files]
Source: "*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{group}\@AppName@"; Filename: "{app}\@AppName@.exe"; WorkingDir: "{app}"
