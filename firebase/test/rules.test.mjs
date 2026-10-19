// Security-rule tests (P7) — run against the Firestore emulator: `npm test` in this folder.
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { readFileSync } from 'node:fs';
import { before, after, beforeEach, describe, it } from 'node:test';
import { doc, setDoc, getDoc, updateDoc, deleteDoc, collection, getDocs, serverTimestamp } from 'firebase/firestore';

let env;
const NOW = 1_700_000_000_000;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-petdiary',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'), host: '127.0.0.1', port: 8080 },
  });
});
after(async () => env.cleanup());
beforeEach(async () => env.clearFirestore());

const db = (uid) => (uid ? env.authenticatedContext(uid).firestore() : env.unauthenticatedContext().firestore());
const group = (over = {}) => ({ ownerUid: 'alice', memberUids: ['alice'], createdAt: NOW, updatedAt: NOW, ...over });
const pet = (id = 'p1', over = {}) => ({ id, name: '雷米', species: 'CAT', birthEpochDay: 19000, createdAt: NOW, updatedAt: NOW, serverUpdatedAt: serverTimestamp(), ...over });
const record = (id = 'r1', over = {}) => ({ id, petId: 'p1', type: 'FEEDING', dateEpochDay: 20000, minuteOfDay: 480, createdAt: NOW, updatedAt: NOW, serverUpdatedAt: serverTimestamp(), ...over });
const reminder = (id = 'm1', over = {}) => ({ id, petId: 'p1', type: 'VET', dueEpochDay: 20001, minuteOfDay: 540, repeat: 'NONE', createdAt: NOW, updatedAt: NOW, serverUpdatedAt: serverTimestamp(), ...over });

/** Alice owns group g1 (set up with rules disabled, as the app's first launch would do through the rules). */
async function seedGroup(extra = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'groups/g1'), group(extra));
  });
}

describe('group creation', () => {
  it('a signed-in user can create a group they own', async () => {
    await assertSucceeds(setDoc(doc(db('alice'), 'groups/g1'), group()));
  });
  it('anonymous (not signed in) cannot', async () => {
    await assertFails(setDoc(doc(db(null), 'groups/g1'), group()));
  });
  it('cannot create a group owned by someone else', async () => {
    await assertFails(setDoc(doc(db('mallory'), 'groups/g1'), group()));
  });
  it('cannot create a group with extra members', async () => {
    await assertFails(setDoc(doc(db('alice'), 'groups/g1'), group({ memberUids: ['alice', 'mallory'] })));
  });
  it('cannot sneak in unknown fields', async () => {
    await assertFails(setDoc(doc(db('alice'), 'groups/g1'), group({ admin: true })));
  });
});

describe('group access', () => {
  beforeEach(() => seedGroup({ memberUids: ['alice', 'bob'] }));
  it('members can read the group', async () => {
    await assertSucceeds(getDoc(doc(db('bob'), 'groups/g1')));
  });
  it('strangers and signed-out users cannot', async () => {
    await assertFails(getDoc(doc(db('mallory'), 'groups/g1')));
    await assertFails(getDoc(doc(db(null), 'groups/g1')));
  });
  it('membership is managed through members/{uid}, so memberUids itself can never be edited', async () => {
    await assertFails(updateDoc(doc(db('alice'), 'groups/g1'), { memberUids: ['alice', 'bob', 'carol'] }));
    await assertFails(updateDoc(doc(db('bob'), 'groups/g1'), { memberUids: ['alice', 'bob', 'bob2'] }));
    await assertFails(updateDoc(doc(db('alice'), 'groups/g1'), { memberUids: ['bob'] }));
  });
  it('the owner can set a display name; others cannot touch the group', async () => {
    await assertSucceeds(updateDoc(doc(db('alice'), 'groups/g1'), { ownerName: '媽媽' }));
    await assertFails(updateDoc(doc(db('bob'), 'groups/g1'), { ownerName: '偽裝' }));
  });
  it('a member cannot make themselves the owner', async () => {
    await assertFails(updateDoc(doc(db('bob'), 'groups/g1'), { ownerUid: 'bob' }));
  });
  it('the owner cannot be replaced', async () => {
    await assertFails(updateDoc(doc(db('alice'), 'groups/g1'), { ownerUid: 'bob' }));
  });
  it('only the owner can delete the group', async () => {
    await assertFails(deleteDoc(doc(db('bob'), 'groups/g1')));
    await assertSucceeds(deleteDoc(doc(db('alice'), 'groups/g1')));
  });
});

describe('data inside a group', () => {
  beforeEach(() => seedGroup({ memberUids: ['alice', 'bob'] }));
  const cases = [
    ['pets', pet], ['records', record], ['reminders', reminder],
    ['supplements', (id = 's1') => ({ id, name: '魚油', createdAt: NOW, updatedAt: NOW, serverUpdatedAt: serverTimestamp() })],
  ];
  for (const [coll, make] of cases) {
    it(`${coll}: members read, write and delete`, async () => {
      const ref = doc(db('bob'), `groups/g1/${coll}/x1`);
      await assertSucceeds(setDoc(ref, make('x1')));
      await assertSucceeds(getDoc(doc(db('alice'), `groups/g1/${coll}/x1`)));
      await assertSucceeds(updateDoc(ref, { updatedAt: NOW + 1, serverUpdatedAt: serverTimestamp() }));
      await assertSucceeds(deleteDoc(ref));
    });
    it(`${coll}: strangers and signed-out users get nothing`, async () => {
      await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), `groups/g1/${coll}/x1`), make('x1')));
      for (const who of ['mallory', null]) {
        await assertFails(getDoc(doc(db(who), `groups/g1/${coll}/x1`)));
        await assertFails(getDocs(collection(db(who), `groups/g1/${coll}`)));
        await assertFails(setDoc(doc(db(who), `groups/g1/${coll}/x2`), make('x2')));
        await assertFails(deleteDoc(doc(db(who), `groups/g1/${coll}/x1`)));
      }
    });
    it(`${coll}: the id field must match the document id`, async () => {
      await assertFails(setDoc(doc(db('alice'), `groups/g1/${coll}/x1`), make('other')));
    });
    it(`${coll}: unknown fields are rejected`, async () => {
      await assertFails(setDoc(doc(db('alice'), `groups/g1/${coll}/x1`), { ...make('x1'), evil: 'x' }));
    });
  }
  it('a member of another group cannot read this group\'s data', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'groups/g2'), group({ ownerUid: 'mallory', memberUids: ['mallory'] }));
      await setDoc(doc(ctx.firestore(), 'groups/g1/pets/p1'), pet());
    });
    await assertFails(getDoc(doc(db('mallory'), 'groups/g1/pets/p1')));
  });
});

describe('field validation', () => {
  beforeEach(() => seedGroup());
  const put = (coll, data) => setDoc(doc(db('alice'), `groups/g1/${coll}/${data.id}`), data);
  it('a pet needs a name and a numeric birthday', async () => {
    await assertSucceeds(put('pets', pet('p1')));
    const { name, ...noName } = pet('p2');
    await assertFails(put('pets', noName));
    await assertFails(put('pets', pet('p3', { birthEpochDay: 'yesterday' })));
  });
  it('strings are length-limited', async () => {
    await assertFails(put('pets', pet('p1', { note: 'x'.repeat(2001) })));
    await assertSucceeds(put('pets', pet('p1', { note: 'x'.repeat(2000) })));
  });
  it('timestamps must be integers', async () => {
    await assertFails(put('records', record('r1', { updatedAt: 'now' })));
  });
  it('minute of day must be within a day', async () => {
    await assertFails(put('records', record('r1', { minuteOfDay: 1440 })));
    await assertFails(put('records', record('r2', { minuteOfDay: -1 })));
    await assertSucceeds(put('records', record('r3', { minuteOfDay: 1439 })));
  });
  it('a full walk record is accepted', async () => {
    await assertSucceeds(put('records', record('r1', { type: 'WALK', durationMin: 30, drankWater: true, peed: true, pooped: false })));
  });
  it('the device-only notifiedFor mark never goes to the cloud', async () => {
    await assertFails(put('reminders', reminder('m1', { notifiedFor: NOW })));
  });
  it('the cloud clock cannot be forged or left out (D55)', async () => {
    await assertFails(put('records', record('r1', { serverUpdatedAt: 5 })));
    await assertFails(put('records', record('r2', { serverUpdatedAt: new Date(2030, 0, 1) })));
    const { serverUpdatedAt, ...none } = record('r3');
    await assertFails(put('records', none));
    await assertSucceeds(put('records', record('r4')));
  });
  it('an edit must also refresh the cloud clock', async () => {
    await assertSucceeds(put('records', record('r1')));
    await assertFails(updateDoc(doc(db('alice'), 'groups/g1/records/r1'), { updatedAt: NOW + 1 })); // stale serverUpdatedAt
  });
  it('soft-deleted rows (deletedAt) are accepted', async () => {
    await assertSucceeds(put('records', record('r1', { deletedAt: NOW + 5 })));
  });
});

describe('user pointer', () => {
  it('a user can point at a group they belong to, and only their own pointer', async () => {
    await seedGroup();
    await assertSucceeds(setDoc(doc(db('alice'), 'users/alice'), { groupId: 'g1', updatedAt: NOW }));
    await assertSucceeds(getDoc(doc(db('alice'), 'users/alice')));
    await assertFails(getDoc(doc(db('mallory'), 'users/alice')));
    await assertFails(setDoc(doc(db('mallory'), 'users/mallory'), { groupId: 'g1', updatedAt: NOW })); // not a member
    await assertFails(setDoc(doc(db('mallory'), 'users/alice'), { groupId: 'g1', updatedAt: NOW }));
  });
});

describe('everything else is closed', () => {
  it('unknown top-level collections', async () => {
    await assertFails(setDoc(doc(db('alice'), 'secrets/x'), { a: 1 }));
    await assertFails(getDoc(doc(db('alice'), 'secrets/x')));
  });
});

// ---- P8: family invitations ----------------------------------------------------------------------------
import { writeBatch, Timestamp } from 'firebase/firestore';

const CODE = 'abcdefghijklmnopqrstuvwx'; // 24 chars, like the app's random codes
const inHours = (h) => Timestamp.fromMillis(Date.now() + h * 3600_000);

async function seedInvite(over = {}, code = CODE) {
  await env.withSecurityRulesDisabled((ctx) =>
    setDoc(doc(ctx.firestore(), `groups/g1/invites/${code}`), { createdAt: Timestamp.now(), expiresAt: inHours(24), used: false, ...over }));
}
const joinBatch = (uid, { code = CODE, name = '媽媽', member = {} } = {}) => {
  const d = db(uid);
  const b = writeBatch(d);
  b.update(doc(d, `groups/g1/invites/${code}`), { used: true, usedBy: uid });
  b.set(doc(d, `groups/g1/members/${uid}`), { name, role: 'member', joinedAt: serverTimestamp(), inviteCode: code, ...member });
  return b.commit();
};

describe('family: invitations', () => {
  beforeEach(() => seedGroup());
  it('the owner creates an invitation that expires within 7 days', async () => {
    const ref = doc(db('alice'), `groups/g1/invites/${CODE}`);
    await assertSucceeds(setDoc(ref, { createdAt: serverTimestamp(), expiresAt: inHours(24), used: false }));
    await assertFails(setDoc(doc(db('alice'), `groups/g1/invites/${CODE}2`), { createdAt: serverTimestamp(), expiresAt: inHours(24 * 8), used: false }));
    await assertFails(setDoc(doc(db('alice'), `groups/g1/invites/${CODE}3`), { createdAt: serverTimestamp(), expiresAt: inHours(-1), used: false }));
  });
  it('codes must be long enough to be unguessable', async () => {
    await assertFails(setDoc(doc(db('alice'), 'groups/g1/invites/short'), { createdAt: serverTimestamp(), expiresAt: inHours(1), used: false }));
  });
  it('others cannot create invitations for a group', async () => {
    await assertFails(setDoc(doc(db('mallory'), `groups/g1/invites/${CODE}`), { createdAt: serverTimestamp(), expiresAt: inHours(1), used: false }));
  });
  it('anyone signed in can fetch an invitation by its code, but only the owner can list them', async () => {
    await seedInvite();
    await assertSucceeds(getDoc(doc(db('bob'), `groups/g1/invites/${CODE}`)));
    await assertFails(getDocs(collection(db('bob'), 'groups/g1/invites')));
    await assertSucceeds(getDocs(collection(db('alice'), 'groups/g1/invites')));
    await assertFails(getDoc(doc(db(null), `groups/g1/invites/${CODE}`)));
  });
  it('the owner can revoke an invitation', async () => {
    await seedInvite();
    await assertFails(deleteDoc(doc(db('bob'), `groups/g1/invites/${CODE}`)));
    await assertSucceeds(deleteDoc(doc(db('alice'), `groups/g1/invites/${CODE}`)));
  });
});

describe('family: joining', () => {
  beforeEach(() => seedGroup());
  it('a valid invitation lets someone join, exactly once', async () => {
    await seedInvite();
    await assertSucceeds(joinBatch('bob'));
    await assertSucceeds(getDoc(doc(db('bob'), 'groups/g1')));
    await assertSucceeds(setDoc(doc(db('bob'), 'groups/g1/pets/p1'), pet()));
    await assertFails(joinBatch('carol')); // already used
  });
  it('an expired invitation is useless', async () => {
    await seedInvite({ expiresAt: inHours(-1) });
    await assertFails(joinBatch('bob'));
  });
  it('joining without using the invitation in the same batch is refused', async () => {
    await seedInvite();
    await assertFails(setDoc(doc(db('bob'), 'groups/g1/members/bob'), { name: 'x', role: 'member', joinedAt: serverTimestamp(), inviteCode: CODE }));
  });
  it('marking an invitation used without joining is fine only for the person marking it', async () => {
    await seedInvite();
    await assertFails(updateDoc(doc(db('bob'), `groups/g1/invites/${CODE}`), { used: true, usedBy: 'carol' }));
  });
  it('cannot join as someone else, or with a made-up role', async () => {
    await seedInvite();
    const d = db('bob');
    const b = writeBatch(d);
    b.update(doc(d, `groups/g1/invites/${CODE}`), { used: true, usedBy: 'bob' });
    b.set(doc(d, 'groups/g1/members/carol'), { name: 'x', role: 'member', joinedAt: serverTimestamp(), inviteCode: CODE });
    await assertFails(b.commit());
    await assertFails(joinBatch('bob', { member: { role: 'owner' } }));
  });
  it('the signed-in user cannot rewrite the invitation to extend it', async () => {
    await seedInvite();
    await assertFails(updateDoc(doc(db('bob'), `groups/g1/invites/${CODE}`), { used: true, usedBy: 'bob', expiresAt: inHours(100) }));
  });
  it('a stranger still cannot read the group or its data', async () => {
    await seedInvite();
    await assertFails(getDoc(doc(db('bob'), 'groups/g1')));
    await assertFails(getDocs(collection(db('bob'), 'groups/g1/pets')));
  });
});

describe('family: members', () => {
  beforeEach(async () => {
    await seedGroup();
    await seedInvite();
    await joinBatch('bob');
  });
  it('members see each other; strangers do not', async () => {
    await assertSucceeds(getDocs(collection(db('bob'), 'groups/g1/members')));
    await assertSucceeds(getDocs(collection(db('alice'), 'groups/g1/members')));
    await assertFails(getDocs(collection(db('mallory'), 'groups/g1/members')));
  });
  it('a member can rename themself but not others', async () => {
    await assertSucceeds(updateDoc(doc(db('bob'), 'groups/g1/members/bob'), { name: '爸爸' }));
    await assertFails(updateDoc(doc(db('bob'), 'groups/g1/members/bob'), { role: 'owner' }));
    await assertFails(updateDoc(doc(db('alice'), 'groups/g1/members/bob'), { name: '被改名' }));
  });
  it('the owner can remove a member; a member can leave; others cannot remove anyone', async () => {
    await assertFails(deleteDoc(doc(db('mallory'), 'groups/g1/members/bob')));
    await assertSucceeds(deleteDoc(doc(db('alice'), 'groups/g1/members/bob')));
    await assertFails(getDoc(doc(db('bob'), 'groups/g1')), 'removed members lose access');
  });
  it('a member can leave on their own', async () => {
    await assertSucceeds(deleteDoc(doc(db('bob'), 'groups/g1/members/bob')));
    await assertFails(getDoc(doc(db('bob'), 'groups/g1/pets/p1')));
  });
  it('members cannot invite others or change the group', async () => {
    await assertFails(setDoc(doc(db('bob'), `groups/g1/invites/${CODE}x`), { createdAt: serverTimestamp(), expiresAt: inHours(1), used: false }));
    await assertFails(updateDoc(doc(db('bob'), 'groups/g1'), { memberUids: ['alice', 'bob', 'eve'] }));
    await assertFails(deleteDoc(doc(db('bob'), 'groups/g1')));
  });
  it('a member can point their user record at the family group', async () => {
    await assertSucceeds(setDoc(doc(db('bob'), 'users/bob'), { groupId: 'g1', updatedAt: NOW }));
  });
  it('record authors can be stored', async () => {
    await assertSucceeds(setDoc(doc(db('bob'), 'groups/g1/records/r1'), record('r1', { authorUid: 'bob' })));
  });
});
