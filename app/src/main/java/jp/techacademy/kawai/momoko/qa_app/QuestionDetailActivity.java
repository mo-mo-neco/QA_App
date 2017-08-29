package jp.techacademy.kawai.momoko.qa_app;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ListView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class QuestionDetailActivity extends AppCompatActivity {

    private ListView mListView;
    private FloatingActionButton mFavo;
    private Question mQuestion;
    private QuestionDetailListAdapter mAdapter;

    private DatabaseReference mAnswerRef;

    private ChildEventListener mEventListener = new ChildEventListener() {
        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String s) {
            HashMap map = (HashMap) dataSnapshot.getValue();

            String answerUid = dataSnapshot.getKey();

            for(Answer answer : mQuestion.getAnswers()) {
                // 同じAnswerUidのものが存在しているときは何もしない
                if (answerUid.equals(answer.getAnswerUid())) {
                    return;
                }
            }
            String body = (String) map.get("body");
            String name = (String) map.get("name");
            String uid = (String) map.get("uid");

            Answer answer = new Answer(body, name, uid, answerUid);
            mQuestion.getAnswers().add(answer);
            mAdapter.notifyDataSetChanged();
        }
        @Override
        public void onChildChanged(DataSnapshot dataSnapshot, String s) {

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
        setContentView(R.layout.activity_question_detail);

        // 渡ってきたQuestionのオブジェクトを保持する
        Bundle extras = getIntent().getExtras();
        mQuestion = (Question) extras.get("question");

        setTitle(mQuestion.getTitle());

        // ListViewの準備
        mListView = (ListView) findViewById(R.id.listView);
        mAdapter = new QuestionDetailListAdapter(this, mQuestion);
        mListView.setAdapter(mAdapter);
        mAdapter.notifyDataSetChanged();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // ログイン済みのユーザーを取得する
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

                if (user == null) {
                    // ログインしていなければログイン画面に遷移させる
                    Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
                    startActivity(intent);
                } else {
                    // Questionを渡して回答作成画面を起動する
                    Intent intent = new Intent(getApplicationContext(), AnswerSendActivity.class);
                    intent.putExtra("question", mQuestion);
                    startActivity(intent);
                }
            }
        });

        DatabaseReference dataBaseReference = FirebaseDatabase.getInstance().getReference();
        mAnswerRef = dataBaseReference.child(Const.ContentsPATH).child(String.valueOf(mQuestion.getGenre())).child(mQuestion.getQuestionUid()).child(Const.AnswersPATH);
        mAnswerRef.addChildEventListener(mEventListener);


        mFavo = (FloatingActionButton) findViewById(R.id.favo);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            // ログインしていなければお気に入りに登録できない
            mFavo.hide();
        }else{
            mFavo.show();
            DatabaseReference favoRef = dataBaseReference.child(Const.UsersPATH).child(user.getUid()).child(Const.FavoritesPATH);
            favoRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    for (DataSnapshot postSnapshot: dataSnapshot.getChildren()) {
                        String strID = postSnapshot.getValue().toString();
                        if (strID.substring(5, strID.length()-1).equals(mQuestion.getQuestionUid())){
                            mFavo.setImageResource(R.drawable.favo_on);
                            postSnapshot.getRef().removeValue();
                            return;
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }
        mFavo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user == null){
                    return;
                }
                DatabaseReference dataBaseReference = FirebaseDatabase.getInstance().getReference();
                DatabaseReference favoRef = dataBaseReference.child(Const.UsersPATH).child(user.getUid()).child(Const.FavoritesPATH);
                favoRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        for (DataSnapshot postSnapshot: dataSnapshot.getChildren()) {
                            String strID = postSnapshot.getValue().toString();
                            if (strID.substring(5, strID.length()-1).equals(mQuestion.getQuestionUid())){
                                mFavo.setImageResource(R.drawable.favo_off);
                                postSnapshot.getRef().removeValue();
                                return;
                            }
                        }
                        mFavo.setImageResource(R.drawable.favo_on);
                        Map<String, String> data = new HashMap<String, String>();
                        data.put("qid", mQuestion.getQuestionUid());
                        dataSnapshot.getRef().push().setValue(data);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
            }
        });
    }
}
