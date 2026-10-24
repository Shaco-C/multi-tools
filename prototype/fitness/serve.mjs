import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {resolve,dirname,extname,sep} from 'node:path';
const root=dirname(fileURLToPath(import.meta.url));
const content=resolve(root,'../../content/fitness/pilot');
const types={'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.mjs':'text/javascript; charset=utf-8','.svg':'image/svg+xml','.png':'image/png','.webp':'image/webp','.json':'application/json; charset=utf-8'};
const server=createServer(async(req,res)=>{try{const url=new URL(req.url,'http://127.0.0.1'),path=decodeURIComponent(url.pathname);let base=root,relative=path==='/'?'index.html':path.slice(1);if(path.startsWith('/content/fitness/pilot/')){base=content;relative=path.slice('/content/fitness/pilot/'.length)}else if(path.startsWith('/media/')||path.startsWith('/muscles/')){base=content;relative=path.slice(1)}const file=resolve(base,relative);if(!file.startsWith(base+sep)){res.writeHead(403);res.end('Forbidden');return}const body=await readFile(file);res.writeHead(200,{'Content-Type':types[extname(file)]??'application/octet-stream','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(body)}catch{res.writeHead(404);res.end('Not found')}});
server.listen(5183,'127.0.0.1',()=>console.log('居家健身原型 http://127.0.0.1:5183'));
