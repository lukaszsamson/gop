# Pick
## Info
Env setup in vagrant vm based on ubuntu 14.10 with docker installed.  
Every component is installed and configured in separate docker containers.  
Scripts to create and run containers included (TODO chef/puppet/vagrant provisioning)  
All functionality from hackaton demo reimplemented in golang backed by redis.  
Site served by nginx:
- Static files & uploaded photos directly
- api by reverse proxy

## Install

1. Download & install vagrant  
https://www.vagrantup.com/downloads.html
2. Dovnload & install virtualbox  
https://www.virtualbox.org/wiki/Downloads
3. In main dir (on windows bash with admin rights)
```
vagrant up
vagrant ssh
```
4. In vagrant vm
```
cd /vagrant
```
5. Build & run uploads storage support
```
./builduploads
./runuploads
```
6. Run redis server
```
./runredis
```
7. Build & run api server
```
./buildapi
./runapi
```
8. Build & run nginx
```
./buildnginx
./runnginx
```
9. Run nodejs env (this will enter  bash in container)
```
./runnodedevenv
```
10. Install packages
```
npm install
bower install --allow-root
```
11. [ctrl+d] to exit node container

12. Browse to
```
http://localhost:8000/
```
## Troubleshooting
- Cannot exec ./cmd
```
chmod +x cmd
```
-npm install fails with cannot create symlink on windows
you need admin rights, npm tries to create symlinks in synchronized vagrant folder
- ./runx fails with The name x is already assigned
```
docker stop x
docker rm x
```
- logs
```
docker logs <container>
```
