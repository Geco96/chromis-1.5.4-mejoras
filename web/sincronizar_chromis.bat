@echo off
REM Ejecutar en la PC del punto de venta. Editar los valores antes de usar.
set SUPABASE_URL=https://TU-PROYECTO.supabase.co
set SUPABASE_KEY=TU_SERVICE_ROLE_KEY
set CHROMIS_DB_URL=mysql://usuario:clave@localhost:3306/chromispos
set SYNC_INTERVALO_MIN=5
cd /d %~dp0
python sync_local.py
pause
