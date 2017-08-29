package jp.techacademy.kawai.momoko.qa_app;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;

import static java.lang.String.valueOf;

public class MainActivity extends AppCompatActivity {
    private Toolbar mToolbar;
    private int mGenre = 0;

    private DatabaseReference mDatabaseReference;
    private DatabaseReference mGenreRef;
    private ListView mListView;
    private ArrayList<Question> mQuestionArrayList;
    private QuestionsListAdapter mAdapter;

    private ChildEventListener mEventListener = new ChildEventListener() {
        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String s) {
            getQuestionData(dataSnapshot, mGenre);
            mAdapter.notifyDataSetChanged();
        }

        @Override
        public void onChildChanged(DataSnapshot dataSnapshot, String s) {
            HashMap map = (HashMap) dataSnapshot.getValue();            // 変更があったQuestionを探す
            for (Question question: mQuestionArrayList) {
                if (dataSnapshot.getKey().equals(question.getQuestionUid())) {
                    // このアプリで変更がある可能性があるのは回答(Answer)のみ
                    question.getAnswers().clear();
                    HashMap answerMap = (HashMap) map.get("answers");
                    if (answerMap != null) {
                        for (Object key : answerMap.keySet()) {
                            HashMap temp = (HashMap) answerMap.get((String) key);
                            String answerBody = (String) temp.get("body");
                            String answerName = (String) temp.get("name");
                            String answerUid = (String) temp.get("uid");
                            Answer answer = new Answer(answerBody, answerName, answerUid, (String) key);
                            question.getAnswers().add(answer);
                        }
                    }

                    mAdapter.notifyDataSetChanged();
                }
            }
        }
        @Override
        public void onChildRemoved(DataSnapshot dataSnapshot) {

        }

        @Override
        public void onChildMoved(DataSnapshot dataSnapshot, String s) {

        }

        @Override
        public void onCancelled(DatabaseError databaseError) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // ジャンルを選択していない場合（mGenre == 0）はエラーを表示するだけ
                if ((mGenre == 0)||(mGenre == 99)) {
                    Snackbar.make(view, "ジャンルを選択して下さい", Snackbar.LENGTH_LONG).show();
                    return;
                }
                // ログイン済みのユーザーを取得する
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

                // ログインしていなければログイン画面に遷移させる
                if (user == null) {
                    Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
                    startActivityForResult(intent, 1);
                } else {
                    // ジャンルを渡して質問作成画面を起動する
                    Intent intent = new Intent(getApplicationContext(), QuestionSendActivity.class);
                    intent.putExtra("genre", mGenre);
                    startActivity(intent);
                }
            }
        });

        // ナビゲーションドロワーの設定
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, mToolbar, R.string.app_name, R.string.app_name);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        drawer.findViewById(R.id.nav_favo);

        // ログインしていなければお気に入り使用不可
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            setFavoStatus(false);
        } else {
            setFavoStatus(true);
        }
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {

            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                int id = item.getItemId();

                if (id == R.id.nav_hobby) {
                    mToolbar.setTitle("趣味");
                    mGenre = 1;
                } else if (id == R.id.nav_life) {
                    mToolbar.setTitle("生活");
                    mGenre = 2;
                } else if (id == R.id.nav_health) {
                    mToolbar.setTitle("健康");
                    mGenre = 3;
                } else if (id == R.id.nav_compter) {
                    mToolbar.setTitle("コンピューター");
                    mGenre = 4;
                } else if (id == R.id.nav_favo) {
                    mToolbar.setTitle("お気に入り");
                    mGenre = 99;
                }

                DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
                drawer.closeDrawer(GravityCompat.START);

                // 質問のリストをクリアしてから再度Adapterにセットし、AdapterをListViewにセットし直す
                mQuestionArrayList.clear();
                mAdapter.setQuestionArrayList(mQuestionArrayList);
                mListView.setAdapter(mAdapter);

                // 選択したジャンルにリスナーを登録する
                if (mGenreRef != null) {
                    mGenreRef.removeEventListener(mEventListener);
                }
                if (mGenre == 99) {
                    // ユーザーのお気に入りを取得する
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null){
                        DatabaseReference favoRef = mDatabaseReference.child(Const.UsersPATH).child(user.getUid()).child(Const.FavoritesPATH);
                        favoRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                for (DataSnapshot postSnapshot: dataSnapshot.getChildren()) {
                                    String strID = postSnapshot.getValue().toString();
                                    strID =strID.substring(5, strID.length()-1);

                                    DatabaseReference questionRef = mDatabaseReference.child(Const.ContentsPATH).child("1");
                                    Query query = questionRef.orderByKey().equalTo(strID);
                                    query.addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(DataSnapshot dataSnapshot) {
                                            for (DataSnapshot postSnapshot: dataSnapshot.getChildren()) {
                                                getQuestionData(postSnapshot, 1);
                                            }
                                            mAdapter.notifyDataSetChanged();
                                        }
                                        @Override
                                        public void onCancelled(DatabaseError databaseError) {

                                        }
                                    });
                                    questionRef = mDatabaseReference.child(Const.ContentsPATH).child("2");
                                    query = questionRef.orderByKey().equalTo(strID);
                                    query.addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(DataSnapshot dataSnapshot) {
                                            for (DataSnapshot postSnapshot: dataSnapshot.getChildren()) {
                                                getQuestionData(postSnapshot, 2);
                                            }
                                            mAdapter.notifyDataSetChanged();
                                        }
                                        @Override
                                        public void onCancelled(DatabaseError databaseError) {

                                        }
                                    });
                                    questionRef = mDatabaseReference.child(Const.ContentsPATH).child("3");
                                    query = questionRef.orderByKey().equalTo(strID);
                                    query.addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(DataSnapshot dataSnapshot) {
                                            for (DataSnapshot postSnapshot: dataSnapshot.getChildren()) {
                                                getQuestionData(postSnapshot, 3);
                                            }
                                            mAdapter.notifyDataSetChanged();
                                        }
                                        @Override
                                        public void onCancelled(DatabaseError databaseError) {

                                        }
                                    });
                                    questionRef = mDatabaseReference.child(Const.ContentsPATH).child("4");
                                    query = questionRef.orderByKey().equalTo(strID);
                                    query.addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(DataSnapshot dataSnapshot) {
                                            for (DataSnapshot postSnapshot: dataSnapshot.getChildren()) {
                                                getQuestionData(postSnapshot, 4);
                                            }
                                            mAdapter.notifyDataSetChanged();
                                        }
                                        @Override
                                        public void onCancelled(DatabaseError databaseError) {

                                        }
                                    });
                                }
                            }
                            @Override
                            public void onCancelled(DatabaseError databaseError) {

                            }
                        });
                    }
                } else{
                    mGenreRef = mDatabaseReference.child(Const.ContentsPATH).child(valueOf(mGenre));
                    mGenreRef.addChildEventListener(mEventListener);
                }

                return true;
            }
        });
        // Firebase
        mDatabaseReference = FirebaseDatabase.getInstance().getReference();

        // ListViewの準備
        mListView = (ListView) findViewById(R.id.listView);
        mAdapter = new QuestionsListAdapter(this);
        mQuestionArrayList = new ArrayList<Question>();

        mAdapter.notifyDataSetChanged();

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Questionのインスタンスを渡して質問詳細画面を起動する
                Intent intent = new Intent(getApplicationContext(), QuestionDetailActivity.class);
                intent.putExtra("question", mQuestionArrayList.get(position));
                startActivity(intent);
            }
        });
        Object obj = (Object) findViewById(R.id.nav_favo);

    }
    @Override
    protected void onActivityResult(int resultCode, int resultCode2, Intent i){
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            setFavoStatus(false);
        } else {
            setFavoStatus(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(getApplicationContext(), SettingActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void getQuestionData(DataSnapshot idataSnapshot, int genre){
        if (idataSnapshot.getValue() == null){
            return;
        }
        HashMap map = (HashMap) idataSnapshot.getValue();
        String title = (String) map.get("title");
        String body = (String) map.get("body");
        String name = (String) map.get("name");
        String uid = (String) map.get("uid");
        String imageString = (String) map.get("image");
        byte[] bytes;
        if (imageString != null) {
            bytes = Base64.decode(imageString, Base64.DEFAULT);
        } else {
            bytes = new byte[0];
        }

        ArrayList<Answer> answerArrayList = new ArrayList<Answer>();
        HashMap answerMap = (HashMap) map.get("answers");
        if (answerMap != null) {
            for (Object key : answerMap.keySet()) {
                HashMap temp = (HashMap) answerMap.get((String) key);
                String answerBody = (String) temp.get("body");
                String answerName = (String) temp.get("name");
                String answerUid = (String) temp.get("uid");
                Answer answer = new Answer(answerBody, answerName, answerUid, (String) key);
                answerArrayList.add(answer);
            }
        }
        Question question = new Question(title, body, name, uid, idataSnapshot.getKey(), genre, bytes, answerArrayList);
        mQuestionArrayList.add(question);
    }

    public void setFavoStatus(boolean istatus){
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        Menu menu = navigationView.getMenu();
        MenuItem menuItem = menu.findItem(R.id.nav_favo);
        menuItem.setEnabled(istatus);
    }
}
