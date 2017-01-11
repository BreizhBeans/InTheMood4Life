# InTheMoodForLife Installation guide



## PI image flash & downloads

Download the latest [Raspbian Jessie lite image](https://downloads.raspberrypi.org/raspbian_lite_latest) and write it with dd

    sudo dd if=2017-01-11-raspbian-jessie-lite.img of=/dev/(your sdname ex sdb)

## Enable root login with a ssh Key


   sudo su -
   mkdir /root/.ssh
   echo "<your ssh public key>" > /root/.ssh/authorized_keys
   chmod -R 700 /root/.ssh


Test if root@<rpi-ip> works.
thats all folks, all the next things will be done via ansible

## System config

Disable password authentication, update packages, install VIM, Java8 JDK and delete historical PI account

 
    ansible-playbook  -i hosts -u root  playbook/systemconfig.yml 

## Warp10 install

    ansible-playbook  -i hosts -u root  playbook/warp10.yml 

## Start / Stop

Warp10 starts and stops on boot
