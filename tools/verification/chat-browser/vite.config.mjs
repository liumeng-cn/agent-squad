// 仅供隔离浏览器夹具：合成身份，不验证真实网关登录，禁止作为正常开发/部署配置。
import { defineConfig } from '../../../web/workbench/node_modules/vite/dist/node/index.js'
import vue from '../../../web/workbench/node_modules/@vitejs/plugin-vue/dist/index.mjs'
import fs from 'node:fs'
import { fileURLToPath } from 'node:url'
const probe = JSON.parse(fs.readFileSync('/tmp/squad-browser-probe.json', 'utf8'))
export default defineConfig({ root: fileURLToPath(new URL('../../../web/workbench', import.meta.url)), plugins: [vue(), { name: 'isolated-browser-identity-fixture', configureServer(server) { server.middlewares.use((req,res,next) => { if (req.url === '/api/v1/auth/me') { res.setHeader('Content-Type','application/json'); res.end(JSON.stringify({code:0,data:{userId:'00000000-0000-0000-0000-000000000001',username:'browser-probe',roles:['USER'],csrfToken:'probe-only',mustChangePassword:false},msg:''})); return } next() }) } }], server: { host:'127.0.0.1',port:5199,strictPort:true,proxy:{'/api':{target:'http://127.0.0.1:'+probe.port,changeOrigin:true,headers:{'X-User-Id':'00000000-0000-0000-0000-000000000001','X-User-Role':'USER'}}} } })
