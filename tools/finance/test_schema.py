import pathlib,re,sqlite3,unittest
source=(pathlib.Path(__file__).resolve().parents[2]/'android/app/src/main/java/com/andriy/jarvis/FinanceLedger.java').read_text()
class LedgerSchemaTest(unittest.TestCase):
 def setUp(self):
  self.db=sqlite3.connect(':memory:');self.db.execute('PRAGMA foreign_keys=ON')
  for sql in re.findall(r'db.execSQL\("(CREATE [^"]+)"\)',source):self.db.execute(sql)
  self.db.execute("INSERT INTO accounts VALUES(1,'Готівка','UAH',10000)")
 def add(self,id,account=1,amount=-8500,group=None):
  self.db.execute('INSERT INTO entries VALUES(?,?,?,?,?,?,?,?,0)',(id,account,amount,'Їжа','',1,'expense',group))
 def test_exact_amount(self):
  self.add('a');self.assertEqual(self.db.execute('SELECT opening+amount FROM accounts JOIN entries ON account=accounts.id').fetchone()[0],1500)
 def test_duplicate_id_rejected(self):
  self.add('a')
  with self.assertRaises(sqlite3.IntegrityError):self.add('a')
 def test_unknown_account_rejected(self):
  with self.assertRaises(sqlite3.IntegrityError):self.add('a',99)
 def test_transfer_conservation(self):
  self.db.execute("INSERT INTO accounts VALUES(2,'Картка','UAH',NULL)")
  self.add('out',1,-2000,'g');self.add('in',2,2000,'g')
  self.assertEqual(self.db.execute('SELECT SUM(amount) FROM entries').fetchone()[0],0)
 def test_group_removal(self):
  self.add('a',group='g');self.add('b',group='g')
  self.db.execute('UPDATE entries SET deleted=1 WHERE id=? OR group_id=(SELECT group_id FROM entries WHERE id=?)',('a','a'))
  self.assertEqual(self.db.execute('SELECT SUM(deleted) FROM entries').fetchone()[0],2)
if __name__=='__main__':unittest.main()
