// Storage security-rule tests (P8) — Firestore + Storage emulators: `npm test` in this folder.
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { readFileSync } from 'node:fs';
import { before, after, beforeEach, describe, it } from 'node:test';
import { doc, setDoc } from 'firebase/firestore';
import { ref, uploadBytes, getBytes, deleteObject } from 'firebase/storage';

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-petdiary',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'), host: '127.0.0.1', port: 8080 },
    storage: { rules: readFileSync(new URL('../storage.rules', import.meta.url), 'utf8'), host: '127.0.0.1', port: 9199 },
  });
});
after(async () => env.cleanup());
beforeEach(async () => {
  await env.clearFirestore();
  await env.clearStorage();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'groups/g1'), { ownerUid: 'alice', memberUids: ['alice'], createdAt: 1, updatedAt: 1 });
    await setDoc(doc(ctx.firestore(), 'groups/g1/members/bob'), { name: '爸爸', role: 'member', inviteCode: 'x'.repeat(24) });
  });
});

const store = (uid) => (uid ? env.authenticatedContext(uid).storage() : env.unauthenticatedContext().storage());
const file = (uid, path = 'groups/g1/files/a.jpg') => ref(store(uid), path);
const bytes = (n) => new Uint8Array(n).fill(1);
const jpg = { contentType: 'image/jpeg' };

describe('files in a group', () => {
  it('the owner and joined members can upload and download', async () => {
    await assertSucceeds(uploadBytes(file('alice'), bytes(100), jpg));
    await assertSucceeds(getBytes(file('bob')));
    await assertSucceeds(uploadBytes(file('bob', 'groups/g1/files/b.pdf'), bytes(100), { contentType: 'application/pdf' }));
  });
  it('strangers and signed-out users get nothing', async () => {
    await env.withSecurityRulesDisabled((ctx) => uploadBytes(ref(ctx.storage(), 'groups/g1/files/a.jpg'), bytes(10), jpg));
    for (const who of ['mallory', null]) {
      await assertFails(getBytes(file(who)));
      await assertFails(uploadBytes(file(who, 'groups/g1/files/z.jpg'), bytes(10), jpg));
      await assertFails(deleteObject(file(who)));
    }
  });
  it('someone who left (or was removed) loses access', async () => {
    await assertSucceeds(uploadBytes(file('alice'), bytes(10), jpg));
    await env.withSecurityRulesDisabled((ctx) => import('firebase/firestore').then(({ deleteDoc }) => deleteDoc(doc(ctx.firestore(), 'groups/g1/members/bob'))));
    await assertFails(getBytes(file('bob')));
  });
  it('only images and PDF are accepted', async () => {
    await assertFails(uploadBytes(file('alice', 'groups/g1/files/evil.exe'), bytes(10), { contentType: 'application/x-msdownload' }));
    await assertFails(uploadBytes(file('alice', 'groups/g1/files/t.txt'), bytes(10), { contentType: 'text/plain' }));
  });
  it('a file may be at most 10 MB', async () => {
    await assertSucceeds(uploadBytes(file('alice', 'groups/g1/files/ok.jpg'), bytes(10 * 1024 * 1024), jpg));
    await assertFails(uploadBytes(file('alice', 'groups/g1/files/big.jpg'), bytes(10 * 1024 * 1024 + 1), jpg));
  });
  it('members can delete files', async () => {
    await assertSucceeds(uploadBytes(file('alice'), bytes(10), jpg));
    await assertSucceeds(deleteObject(file('bob')));
  });
  it('another group\'s files are out of reach, and so is any other path', async () => {
    await assertFails(uploadBytes(file('alice', 'groups/g2/files/a.jpg'), bytes(10), jpg));
    await assertFails(uploadBytes(file('alice', 'elsewhere/a.jpg'), bytes(10), jpg));
    await assertFails(uploadBytes(file('alice', 'groups/g1/other/a.jpg'), bytes(10), jpg));
  });
});
