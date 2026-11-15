import pathlib,re,sqlite3,unittest
source=(pathlib.Path(__file__).resolve().parents[2]/'android/app/src/main/java/com/andriy/jarvis/FinanceLedger.java').read_text()
class LedgerSchemaTest(unittest.TestCase):
 def setUp(self):
  self.db=sqlite3.connect(':memory:');self.db.execute('PRAGMA foreign_keys=ON')
  for sql in re.findall(r'db.execSQL\("((?:CREATE |ALTER )[^"]+)"\)',source):
   if sql.startswith('ALTER TABLE accounts ADD COLUMN'):continue
   self.db.execute(sql)
  self.db.execute("INSERT INTO accounts(id,name,currency,opening) VALUES(1,'Готівка','UAH',10000)")
 def add(self,id,account=1,amount=-8500,group=None):
  self.db.execute('INSERT INTO entries VALUES(?,?,?,?,?,?,?,?,0)',(id,account,amount,'Їжа','',1,'expense',group))
 def test_opening_balance_cutoff(self):
  self.db.execute("UPDATE accounts SET opening_at=1000 WHERE id=1")
  self.add('historical',amount=-5000)
  self.db.execute("INSERT INTO entries VALUES('current',1,-1000,'Їжа','',1001,'expense',NULL,0)")
  balance=self.db.execute("SELECT a.opening+COALESCE(SUM(CASE WHEN e.deleted=0 AND (a.opening_at=0 OR e.happened>=a.opening_at) THEN e.amount ELSE 0 END),0) FROM accounts a LEFT JOIN entries e ON a.id=e.account WHERE a.id=1 GROUP BY a.id").fetchone()[0]
  self.assertEqual(balance,9000)
 def test_migration_adds_cutoff_without_erasing_accounts(self):
  db=sqlite3.connect(':memory:')
  db.execute("CREATE TABLE accounts(id INTEGER PRIMARY KEY,name TEXT,currency TEXT,opening INTEGER)")
  db.execute("INSERT INTO accounts VALUES(1,'Готівка','UAH',5000)")
  db.execute("ALTER TABLE accounts ADD COLUMN opening_at INTEGER NOT NULL DEFAULT 0")
  self.assertEqual(db.execute("SELECT opening,opening_at FROM accounts").fetchone(),(5000,0))
  db.close()
 def test_refund_keeps_earned_income_separate(self):
  self.add('purchase',amount=-8500)
  self.db.execute("INSERT INTO entries VALUES('refund',1,2500,'Їжа','Повернення:purchase',2,'refund',NULL,0)")
  r=self.db.execute("SELECT COALESCE(-SUM(amount),0) FROM entries WHERE kind IN ('expense','refund')").fetchone()[0]
  self.assertEqual(r,6000)
 def test_exact_amount(self):
  self.add('a');self.assertEqual(self.db.execute('SELECT opening+amount FROM accounts JOIN entries ON account=accounts.id').fetchone()[0],1500)
 def test_duplicate_id_rejected(self):
  self.add('a')
  with self.assertRaises(sqlite3.IntegrityError):self.add('a')
 def test_unknown_account_rejected(self):
  with self.assertRaises(sqlite3.IntegrityError):self.add('a',99)
 def test_transfer_conservation(self):
  self.db.execute("INSERT INTO accounts(id,name,currency,opening) VALUES(2,'Картка','UAH',NULL)")
  self.add('out',1,-2000,'g');self.add('in',2,2000,'g')
  self.assertEqual(self.db.execute('SELECT SUM(amount) FROM entries').fetchone()[0],0)
 def test_group_removal(self):
  self.add('a',group='g');self.add('b',group='g')
  self.db.execute('UPDATE entries SET deleted=1 WHERE id=? OR group_id=(SELECT group_id FROM entries WHERE id=?)',('a','a'))
  self.assertEqual(self.db.execute('SELECT SUM(deleted) FROM entries').fetchone()[0],2)
 def test_plan_validation(self):
  with self.assertRaises(sqlite3.IntegrityError):
   self.db.execute("INSERT INTO plans(id,type,name,currency,amount,progress,due,category) VALUES('x','goal','Ціль','UAH',-1,0,'','')")
 def test_restore_rollback(self):
  self.add('keep');self.db.commit()
  try:
   with self.db:
    self.db.execute('DELETE FROM entries')
    self.add('bad',99)
  except sqlite3.IntegrityError:pass
  self.assertEqual(self.db.execute('SELECT id FROM entries').fetchone()[0],'keep')
 def test_unknown_balance_preserved(self):
  self.db.execute("INSERT INTO accounts(id,name,currency,opening) VALUES(2,'Карта','UAH',NULL)")
  self.assertIsNone(self.db.execute('SELECT opening FROM accounts WHERE id=2').fetchone()[0])
 def test_receipt_unique(self):
  self.db.execute("INSERT INTO receipts VALUES('watch:1','витрата кава 85','ok')")
  with self.assertRaises(sqlite3.IntegrityError):self.db.execute("INSERT INTO receipts VALUES('watch:1','витрата кава 85','ok')")
 def test_allocation_requires_real_plan(self):
  with self.assertRaises(sqlite3.IntegrityError):self.db.execute("INSERT INTO allocations VALUES('x','missing',1,100)")
 def test_failed_settlement_rolls_back(self):
  self.db.execute("INSERT INTO plans(id,type,name,currency,amount,due,category) VALUES('g','goal','Ціль','UAH',10000,'','')");self.db.commit()
  try:
   with self.db:
    self.db.execute("UPDATE plans SET progress=500 WHERE id='g'")
    self.db.execute("INSERT INTO allocations VALUES('x','g',99,500)")
  except sqlite3.IntegrityError:pass
  self.assertEqual(self.db.execute("SELECT progress FROM plans WHERE id='g'").fetchone()[0],0)
 def test_v5_schema_has_integrity_fields(self):
  objects={r[0] for r in self.db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
  self.assertTrue({'finance_shifts','crypto_positions','fx_transfers','settlement_history'}.issubset(objects))
  cols={r[1] for r in self.db.execute("PRAGMA table_info(plans)")}
  self.assertTrue({'budget_period','recurrence_unit','recurrence_every','recurrence_anchor'}.issubset(cols))
  columns={r[1] for r in self.db.execute("PRAGMA table_info(settlement_history)")}
  self.assertIn('reversed',columns)
 def test_fx_transfer_and_fee_group(self):
  self.db.execute("INSERT INTO accounts(id,name,currency,opening) VALUES(2,'Euro','EUR',20000)")
  self.db.execute("INSERT INTO fx_transfers VALUES('fx1',1,2,4300,100,20,5000)")
  self.db.execute("INSERT INTO entries(id,account,amount,category,note,happened,kind,group_id) VALUES('fx-out',1,-4300,'Обмін','',5000,'transfer','fx1')")
  self.db.execute("INSERT INTO entries(id,account,amount,category,note,happened,kind,group_id) VALUES('fx-in',2,100,'Обмін','',5000,'transfer','fx1')")
  self.db.execute("INSERT INTO entries(id,account,amount,category,note,happened,kind,group_id) VALUES('fx-fee',1,-20,'Комісії','',5000,'expense','fx1')")
  rows=self.db.execute("SELECT account,SUM(amount) FROM entries WHERE group_id='fx1' GROUP BY account ORDER BY account").fetchall()
  self.assertEqual(rows,[(1,-4320),(2,100)])
  with self.assertRaises(sqlite3.IntegrityError):
   self.db.execute("INSERT INTO fx_transfers VALUES('invalid',1,999,10,10,0,5000)")
 def test_settlement_history_reversal_state(self):
  self.db.execute("INSERT INTO plans(id,type,name,currency,amount,progress,due,category) VALUES('rent','payment','Оренда','UAH',12000,12000,'2027-01-01','Житло')")
  self.db.execute("INSERT INTO entries(id,account,amount,category,note,happened,kind,group_id) VALUES('paid',1,-12000,'Житло','Оренда',1000,'expense','plan:rent')")
  self.db.execute("INSERT INTO settlement_history(entry_id,plan_id,before_progress,after_progress,before_due,after_due) VALUES('paid','rent',0,12000,'2027-01-01','2027-01-01')")
  with self.db:
   self.db.execute("UPDATE entries SET deleted=1 WHERE id='paid'")
   self.db.execute("UPDATE plans SET progress=0 WHERE id='rent'")
   self.db.execute("UPDATE settlement_history SET reversed=1 WHERE entry_id='paid'")
  self.assertEqual(self.db.execute("SELECT progress FROM plans WHERE id='rent'").fetchone()[0],0)
  self.assertEqual(self.db.execute("SELECT reversed FROM settlement_history WHERE entry_id='paid'").fetchone()[0],1)
  self.assertEqual(self.db.execute("SELECT deleted FROM entries WHERE id='paid'").fetchone()[0],1)
 def test_grouped_shift_tips_not_double_counted(self):
  self.db.execute("INSERT INTO accounts(id,name,currency,opening) VALUES(2,'Картка','UAH',1000)")
  self.db.execute("INSERT INTO finance_shifts VALUES('shift1','Вечір',2000,'8')")
  self.db.execute("INSERT INTO entries(id,account,amount,category,note,happened,kind,group_id) VALUES('cash',1,6500,'чай','',2000,'income','shift1')")
  self.db.execute("INSERT INTO entries(id,account,amount,category,note,happened,kind,group_id) VALUES('card',2,3500,'чай','',2000,'income','shift1')")
  self.assertEqual(self.db.execute("SELECT SUM(amount) FROM entries WHERE group_id='shift1'").fetchone()[0],10000)
  self.assertEqual(self.db.execute("SELECT COUNT(*) FROM finance_shifts").fetchone()[0],1)
 def test_account_aliases_integrity(self):
  cols={r[1] for r in self.db.execute("PRAGMA table_info(finance_aliases)")}
  self.assertTrue({'alias','account'}.issubset(cols))
  self.db.execute("INSERT INTO finance_aliases VALUES('монобанк',1)")
  with self.assertRaises(sqlite3.IntegrityError):
   self.db.execute("INSERT INTO finance_aliases VALUES('монобанк',1)")
  with self.assertRaises(sqlite3.IntegrityError):
   self.db.execute("INSERT INTO finance_aliases VALUES('готівка',99)")
 def test_v6_crypto_trades_and_cash_journal_integrity(self):
  tables={r[0] for r in self.db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
  self.assertIn('crypto_trades',tables)
  self.db.execute("INSERT INTO crypto_trades(id,symbol,action,quantity,price_uah_minor,fee_uah_minor,account,happened) VALUES('t1','BTC','buy','0.125',125000,500,1,100)")
  self.db.execute("INSERT INTO entries(id,account,amount,category,note,happened,kind,group_id) VALUES('t1:asset',1,-125000,'Криптоактиви','',100,'asset','t1')")
  self.db.execute("INSERT INTO entries(id,account,amount,category,note,happened,kind,group_id) VALUES('t1:fee',1,-500,'Комісії','',100,'expense','t1')")
  self.assertEqual(self.db.execute("SELECT SUM(amount) FROM entries WHERE group_id='t1'").fetchone()[0],-125500)
  self.assertEqual(self.db.execute("SELECT COALESCE(-SUM(amount),0) FROM entries WHERE kind IN ('expense','refund')").fetchone()[0],500)
  with self.assertRaises(sqlite3.IntegrityError):
   self.db.execute("INSERT INTO crypto_trades(id,symbol,action,quantity,price_uah_minor,fee_uah_minor,account,happened) VALUES('t2','BTC','mine','1',50,0,1,100)")
  with self.assertRaises(sqlite3.IntegrityError):
   self.db.execute("INSERT INTO crypto_trades(id,symbol,action,quantity,price_uah_minor,fee_uah_minor,account,happened) VALUES('t3','BTC','buy','1',50,0,999,100)")
 def test_sqlcipher_driver_is_not_plaintext(self):
  import pathlib
  root=pathlib.Path(__file__).resolve().parents[2]
  ledger=(root/'android/app/src/main/java/com/andriy/jarvis/FinanceLedger.java').read_text()
  key=(root/'android/app/src/main/java/com/andriy/jarvis/FinanceSecureKey.java').read_text()
  self.assertIn('net.zetetic.database.sqlcipher.SQLiteOpenHelper',ledger)
  self.assertIn('FinanceLegacyMigration.migrate',ledger)
  self.assertIn('AndroidKeyStore',key)
  self.assertIn('AES/GCM/NoPadding',key)
if __name__=='__main__':unittest.main()
