@echo off
setlocal enabledelayedexpansion

echo ============================================
echo  Step 1/2: Create EKS cluster + node group
echo ============================================
terraform apply -target=aws_eks_cluster.dlv_eks -target=aws_eks_node_group.dlv_node_group -auto-approve

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [STOPPED] Step 1 failed. See the error above.
    echo Fix the issue, then run apply.bat again.
    pause
    exit /b 1
)

echo.
echo ============================================
echo  Step 2/2: Apply remaining resources
echo  (LB Controller, ExternalDNS, k8s manifests)
echo ============================================
terraform apply -auto-approve

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [STOPPED] Step 2 failed. See the error above.
    echo Fix the issue, then run apply.bat again.
    pause
    exit /b 1
)

echo.
echo ============================================
echo  All done!
echo ============================================
pause
