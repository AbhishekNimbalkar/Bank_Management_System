@echo off
title Bank Management System - Backend Stopper
cd /d "%~dp0"
echo Stopping all backend services...
java BackendRunner.java --stop
pause
