// Takes the documentation screenshots from scratch: a throwaway local cloud, a fresh Fabric client in its own run folder,
// a world generated from the seed in client.steps, and a browser on the player website. Run with `npm run screenshots`
// from docs/. Needs Docker, the .NET SDK, JDK 25 and Node; the developer's own local cloud may keep running.

import { spawn, spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:net';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';
import sharp from 'sharp';
import { WEBSITE_STEPS } from './website.steps.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, '../..');
const WORK = join(HERE, '.work');
const OUT = join(ROOT, 'docs/src/assets/screenshots');
const RUN_DIR = join(ROOT, 'src/mod/heavymatica/fabric/runs/docs');
const WINDOWS = process.platform === 'win32';

// Its own ports and container names, so it never meets the developer's local cloud.
const CLOUD_PORT = 5190;
const CLOUD = `http://localhost:${CLOUD_PORT}`;
const MONGO = { name: 'heavymatica-docs-mongo', port: 27190 };
const MINIO = { name: 'heavymatica-docs-minio', port: 9190, user: 'heavymatica', password: 'heavymatica-docs-secret' };
const PLAYER = { name: 'HeavyDocs', uuid: '3f6b2c1e-8d4a-4b7f-9e2c-5a1d0c7e9b42' };

// Optional: another steps file, and --client-only or --website-only to take one half.
const ARGS = process.argv.slice(2);
const STEPS = ARGS.find((arg) => !arg.startsWith('--')) ? resolve(ARGS.find((arg) => !arg.startsWith('--'))) : join(HERE, 'client.steps');
const ONLY_CLIENT = ARGS.includes('--client-only');

const children = [];

function log(message) {
  console.log(`[screenshots] ${message}`);
}

function run(command, args, options = {}) {
  const result = spawnSync(command, args, { stdio: 'inherit', shell: WINDOWS, ...options });
  if (result.status !== 0) throw new Error(`${command} ${args.join(' ')} failed with ${result.status}`);
}

function docker(...args) {
  return spawnSync('docker', args, { encoding: 'utf8' });
}

function portFree(port) {
  return new Promise((done) => {
    const server = createServer()
      .once('error', () => done(false))
      .once('listening', () => server.close(() => done(true)))
      .listen(port, '127.0.0.1');
  });
}

async function waitFor(url, seconds) {
  for (let i = 0; i < seconds; i++) {
    try {
      if ((await fetch(url)).ok) return;
    } catch {
      // not up yet
    }
    await new Promise((done) => setTimeout(done, 1000));
  }
  throw new Error(`${url} did not answer within ${seconds} s`);
}

function removeContainers() {
  for (const { name } of [MONGO, MINIO]) docker('rm', '-f', name);
}

async function startCloud() {
  for (const port of [CLOUD_PORT, MONGO.port, MINIO.port]) {
    if (!(await portFree(port))) throw new Error(`Port ${port} is in use. Stop whatever uses it, or a previous screenshot run.`);
  }
  removeContainers();

  log('Building the player website');
  run('npm', ['run', 'generate'], { cwd: join(ROOT, 'src/web/player') });
  run('npx', ['astro', 'build', '--outDir', join(WORK, 'website')], { cwd: join(ROOT, 'src/web/player') });

  log('Building the cloud');
  run('dotnet', ['build', join(ROOT, 'src/cloud/Heavymatica.Cloud'), '-c', 'Release', '-o', join(WORK, 'cloud'), '-v', 'quiet', '-nologo']);

  log('Starting MongoDB and MinIO');
  for (const args of [
    ['run', '-d', '--rm', '--name', MONGO.name, '-p', `127.0.0.1:${MONGO.port}:27017`, 'mongo:8.3'],
    ['run', '-d', '--rm', '--name', MINIO.name, '-p', `127.0.0.1:${MINIO.port}:9000`, '-e', `MINIO_ROOT_USER=${MINIO.user}`,
      '-e', `MINIO_ROOT_PASSWORD=${MINIO.password}`, 'cgr.dev/chainguard/minio:latest', 'server', '/data'],
  ]) {
    const result = docker(...args);
    if (result.status !== 0) throw new Error(`docker ${args.join(' ')}: ${result.stderr}`);
  }

  log('Starting the cloud');
  const executable = join(WORK, 'cloud', WINDOWS ? 'Heavymatica.Cloud.exe' : 'Heavymatica.Cloud');
  const cloud = spawn(executable, [], {
    cwd: join(WORK, 'cloud'),
    stdio: ['ignore', 'ignore', 'inherit'],
    env: {
      ...process.env,
      ASPNETCORE_ENVIRONMENT: 'Development',
      ASPNETCORE_URLS: CLOUD,
      ConnectionStrings__heavymatica: `mongodb://127.0.0.1:${MONGO.port}/heavymatica?directConnection=true`,
      S3__ServiceUrl: `http://127.0.0.1:${MINIO.port}`,
      S3__AccessKey: MINIO.user,
      S3__SecretKey: MINIO.password,
      S3__Bucket: 'heavymatica',
      Identity__MojangSessionUrl: `${CLOUD}/dev/mojang`,
      Accounts__LinkBaseUrl: CLOUD,
      Status__SignIn: 'open',
      Web__Root: join(WORK, 'website'),
      Thumbnails__GracePeriod: '00:00:05',
    },
  });
  children.push(cloud);
  await waitFor(`${CLOUD}/health`, 120);
  log(`Cloud up on ${CLOUD}`);
}

function prepareRunFolder() {
  rmSync(RUN_DIR, { recursive: true, force: true });
  mkdirSync(join(RUN_DIR, 'config'), { recursive: true });
  // Fixed view settings, and none of the first-start screens.
  writeFileSync(
    join(RUN_DIR, 'options.txt'),
    [
      'onboardAccessibility:false',
      'skipMultiplayerWarning:true',
      'tutorialStep:none',
      'joinedFirstServer:true',
      'guiScale:3',
      'fov:0.0',
      'renderDistance:12',
      'simulationDistance:8',
      'soundCategory_music:0.0',
      'soundCategory_master:0.0',
      'narrator:0',
      'pauseOnLostFocus:false',
      'lang:en_us',
      '',
    ].join('\n'),
  );
  writeFileSync(join(RUN_DIR, 'config/heavymatica-client.toml'), `[cloud]\n\tdevSessionJoinUrl = "${CLOUD}/dev/mojang"\n`);
}

function runClient() {
  log(`Running the client with ${STEPS}`);
  run(join(ROOT, 'src/mod', WINDOWS ? 'gradlew.bat' : 'gradlew'), [':heavymatica:fabric:runDocs', '--console=plain'], {
    cwd: join(ROOT, 'src/mod'),
    env: {
      ...process.env,
      HEAVYMATICA_DEV_SCREEN: `@${STEPS}`,
      HEAVYMATICA_DEV_USER: `${PLAYER.name},${PLAYER.uuid}`,
      HEAVYMATICA_CLOUD_URL: CLOUD,
    },
  });
}

async function runWebsite() {
  log('Taking the website screenshots');
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 800 }, deviceScaleFactor: 1 });
    await page.goto(`${CLOUD}/dev/web/sign-in?minecraft=${PLAYER.uuid}&returnTo=/library/`);
    for (const step of WEBSITE_STEPS) {
      await step.run(page, { cloud: CLOUD });
      await page.waitForTimeout(step.wait ?? 1500);
      await page.screenshot({ path: join(WORK, 'website-shots', `${step.name}.png`) });
      log(`website/${step.name}`);
    }
  } finally {
    await browser.close();
  }
}

// WebP keeps the committed images small; the docs build serves them as they are.
async function collect() {
  // A full run replaces every image, so shots that were renamed or dropped do not linger.
  if (!ONLY_CLIENT) rmSync(OUT, { recursive: true, force: true });
  mkdirSync(OUT, { recursive: true });
  const shots = join(RUN_DIR, 'screenshots');
  for (const [dir, prefix] of [[shots, ''], [join(WORK, 'website-shots'), 'website-']]) {
    if (!existsSync(dir)) continue;
    for (const file of readdirSync(dir).filter((name) => name.endsWith('.png'))) {
      await sharp(join(dir, file)).webp({ quality: 82 }).toFile(join(OUT, prefix + file.replace(/\.png$/, '.webp')));
    }
  }
  log(`Screenshots are in ${OUT}`);
}

function stop() {
  for (const child of children) child.kill();
  removeContainers();
}

process.on('SIGINT', () => {
  stop();
  process.exit(130);
});

try {
  rmSync(join(WORK, 'website-shots'), { recursive: true, force: true });
  mkdirSync(join(WORK, 'website-shots'), { recursive: true });
  await startCloud();
  prepareRunFolder();
  runClient();
  if (!ONLY_CLIENT) await runWebsite();
  await collect();
} finally {
  stop();
}
