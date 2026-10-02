import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const root=dirname(fileURLToPath(import.meta.url));
const files={'/':'index.html','/index.html':'index.html','/style.css':'style.css','/ui.js':'ui.js','/model.js':'model.js','/icons.js':'icons.js'};
const types={'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.js':'text/javascript; charset=utf-8'};
const server=createServer(async(req,res)=>{const pathname=new URL(req.url,'http://127.0.0.1').pathname;const file=files[pathname];if(!file){res.writeHead(404);res.end('Not found');return;}try{const body=await readFile(join(root,file));res.writeHead(200,{'Content-Type':types[file.slice(file.lastIndexOf('.'))],'Cache-Control':'no-store'});res.end(body);}catch{res.writeHead(500);res.end('Unable to read prototype');}});
server.listen(5180,'127.0.0.1',()=>console.log('饮食不适记录原型：http://127.0.0.1:5180'));
