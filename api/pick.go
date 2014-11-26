package main

import (
	"app/snowflake"
	"fmt"
	"github.com/garyburd/redigo/redis"
	"github.com/go-martini/martini"
	"github.com/martini-contrib/binding"
	"github.com/martini-contrib/render"
	"github.com/martini-contrib/sessions"
	"io"
	"io/ioutil"
	"mime/multipart"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"time"
)

func newPool(server, password string) *redis.Pool {
	return &redis.Pool{
		MaxIdle:     3,
		IdleTimeout: 240 * time.Second,
		Dial: func() (redis.Conn, error) {
			c, err := redis.Dial("tcp", server)
			if err != nil {
				return nil, err
			}
			if password != "" {
				if _, err := c.Do("AUTH", password); err != nil {
					c.Close()
					return nil, err
				}
			}
			return c, err
		},
		TestOnBorrow: func(c redis.Conn, t time.Time) error {
			_, err := c.Do("PING")
			return err
		},
	}
}

var scriptCache = make(map[string]string)

func s(fileName string) string {
	cached, exists := scriptCache[fileName]
	if exists {
		return cached
	}

	buf, err := ioutil.ReadFile(fileName)
	if err != nil {
		panic(err)
	}

	loaded := string(buf)
	scriptCache[fileName] = loaded
	return loaded
}

func GetEnvOrDefault(key, def string) string {
	value := os.Getenv(key)
	if value == "" {
		value = def
	}
	return value
}

var (
	pool          *redis.Pool
	instance      = GetEnvOrDefault("INSTANCE", "1")
	uploadsDir    = GetEnvOrDefault("UPLOADS_DIR", "./uploads")
	redisServer   = GetEnvOrDefault("DB_PORT_6379_TCP_ADDR", "localhost")
	redisPort     = GetEnvOrDefault("DB_PORT_6379_TCP_PORT", "6379")
	redisPassword = ""
)

type Question struct {
	Pick1Id string
	Pick2Id string
}

type Vote struct {
	QuestionId string
	PickId     string
}

type NextQuestion struct {
	UserId int
}

type Pick struct {
	Image *multipart.FileHeader `form:"file"`
}

type User struct {
	AccessToken string `json:"accessToken"`
	ExpiresIn int `json:"expiresIn"`
	SignedRequest string `json:"signedRequest"`
	UserID string `json:"userID"`
}

func main() {
	inst, _ := strconv.Atoi(instance)
	fmt.Println("Setting up snowflake with instance id", inst)
	snowflake.Setup(uint8(inst))
	redisCon := redisServer + ":" + redisPort
	fmt.Println("Connecting to redis at " + redisCon)
	fmt.Println("Uploads will be stored in " + uploadsDir)
	pool = newPool(redisCon, redisPassword)

	m := martini.Classic()
	store := sessions.NewCookieStore([]byte("trololo"))
	m.Use(sessions.Sessions("my_session", store))
	m.Map(pool)
	m.Use(func(c martini.Context, pool *redis.Pool) {
		conn := pool.Get()
		c.Map(conn)

		c.Next()
		conn.Close()
	})

	loginHandler := func(c martini.Context, session sessions.Session, r render.Render) {
		userID := session.Get("userID")
		if userID == nil {
			r.JSON(401, map[string]interface{}{"msg": "Not authenticated"})
			return
		}
		user := User{}
		user.UserID = userID.(string)
		if user.UserID == "" {
			panic("userID is empty")
		}

		c.Map(user)
		c.Next()
	}

	m.Use(render.Renderer(render.Options{}))

	m.Post("/login", binding.Json(User{}), func(session sessions.Session, user User, r render.Render) {
		fmt.Printf("%v\n", user.UserID)
		session.Set("userID", user.UserID)
		r.JSON(200, map[string]interface{}{"msg": "Ok"})
	})

	m.Post("/picks", loginHandler, binding.MultipartForm(Pick{}), func(conn redis.Conn, user User, params martini.Params, msg Pick, r render.Render, res http.ResponseWriter) {
		file, err := msg.Image.Open()
		if err != nil {
			panic(err)
		}
		defer file.Close()

		image, err := ioutil.TempFile(uploadsDir, "image_")
		if err != nil {
			panic(err)
		}
		defer image.Close()
		err = image.Chmod(644)
		if err != nil {
			//delete
			os.Remove(image.Name())
			panic(err)
		}
		_, err = io.Copy(image, file)
		if err != nil {
			//delete
			os.Remove(image.Name())
			panic(err)
		}

		u := snowflake.GetId()
		_, err = conn.Do("SET", fmt.Sprintf("picks:%v:image", u), filepath.Base(image.Name()))
		if err != nil {
			os.Remove(image.Name())
			panic(err)
		}

		r.JSON(200, map[string]interface{}{"pickId": fmt.Sprintf("%v", u)})
	})

	// m.Post("/questions", loginHandler, binding.Json(Question{}), func(conn redis.Conn, user User, msg Question, r render.Render) {
	// 	u := snowflake.GetId()
	// 	_, err := conn.Do("EVAL", s("saveQuestion.lua"), 5,
	// 	  fmt.Sprintf("picks:%v:image", msg.Pick1Id),
	// 	  fmt.Sprintf("picks:%v:image", msg.Pick2Id),
	// 	  "questions",
	// 	  fmt.Sprintf("questions:%v", u),
	// 	  fmt.Sprintf("questions:%v:votes", u))
	// 		if err != nil {
	// 			//todo sprawdzić rez i 400
	// 			panic(err)
	// 		}
	//
	// 		r.JSON(200, map[string]interface{}{"questionId": fmt.Sprintf("%v", u)})
	// 		})

	m.Post("/questions", loginHandler, binding.Json(Question{}), func(conn redis.Conn, user User, params martini.Params, msg Question, r render.Render, res http.ResponseWriter) {

		conn.Send("GET", fmt.Sprintf("picks:%v:image", msg.Pick1Id))
		conn.Send("GET", fmt.Sprintf("picks:%v:image", msg.Pick2Id))
		conn.Flush()
		pick1ImageR, err := conn.Receive()
		if err != nil {
			panic(err)
		}
		pick2ImageR, err := conn.Receive()
		if err != nil {
			panic(err)
		}
		if pick1ImageR == nil || pick2ImageR == nil {
			r.JSON(400, map[string]interface{}{
				"msg": "Invalid pick ids",
			})
			return
		}

		pick1Image, err := redis.String(pick1ImageR, nil)
		if err != nil {
			panic(err)
		}
		pick2Image, err := redis.String(pick2ImageR, nil)
		if err != nil {
			panic(err)
		}

		u := snowflake.GetId()
		conn.Send("MULTI")
		conn.Send("SADD", "questions", u)
		conn.Send("HMSET", fmt.Sprintf("questions:%v", u), "pick1Id", msg.Pick1Id, "pick1Image", pick1Image, "pick2Id", msg.Pick2Id, "pick2Image", pick2Image)
		conn.Send("HMSET", fmt.Sprintf("questions:%v:votes", u), msg.Pick1Id, 0, msg.Pick2Id, 0)
		_, err = conn.Do("EXEC")
		if err != nil {
			panic(err)
		}

		r.JSON(200, map[string]interface{}{"questionId": fmt.Sprintf("%v", u)})
	})

	m.Get("/questions/next", loginHandler, func(conn redis.Conn, user User, params martini.Params, r render.Render, res http.ResponseWriter) {
		userId := user.UserID
		pick1Id := ""
		pick2Id := ""
		var pick1Image string
		var pick2Image string

		conn.Send("SDIFFSTORE", fmt.Sprintf("questionsNotAnswered:%v", userId), "questions", fmt.Sprintf("questionsAnswered:%v", userId))
		conn.Send("SRANDMEMBER", fmt.Sprintf("questionsNotAnswered:%v", userId))
		conn.Send("DEL", fmt.Sprintf("questionsNotAnswered:%v", userId))
		conn.Flush()
		_, err := conn.Receive()
		if err != nil {
			panic(err)
		}
		questionIdR, err := conn.Receive()
		if err != nil {
			panic(err)
		}
		_, err = conn.Receive()
		if err != nil {
			panic(err)
		}

		if questionIdR == nil {
			r.JSON(200, map[string]interface{}{
				"msg": "No new questions at this time",
			})
			return
		}

		questionId, err := redis.String(questionIdR, nil)
		if err != nil {
			panic(err)
		}

		r1, err := redis.Values(conn.Do("HMGET", fmt.Sprintf("questions:%v", questionId), "pick1Id", "pick1Image", "pick2Id", "pick2Image"))
		if err != nil {
			panic(err)
		}
		fmt.Println(r1)
		_, err = redis.Scan(r1, &pick1Id, &pick1Image, &pick2Id, &pick2Image)
		if err != nil {
			panic(err)
		}

		r.JSON(200, map[string]interface{}{
			"questionId": questionId,
			"pick1": map[string]interface{}{
				"pickId": pick1Id,
				"image":  pick1Image,
			},
			"pick2": map[string]interface{}{
				"pickId": pick2Id,
				"image":  pick2Image,
			},
		})
	})

	m.Get("/questions/:questionId", loginHandler, func(conn redis.Conn, user User, params martini.Params, r render.Render, res http.ResponseWriter) {
		questionId := params["questionId"]
		pick1Id := ""
		pick2Id := ""
		var pick1Image string
		var pick2Image string
		conn.Send("HMGET", fmt.Sprintf("questions:%v", questionId), "pick1Id", "pick1Image", "pick2Id", "pick2Image")
		conn.Send("HGETALL", fmt.Sprintf("questions:%v:votes", questionId))
		conn.Flush()
		r1, err := redis.Values(conn.Receive())
		if err != nil {
			panic(err)
		}
		fmt.Println(r1)
		if r1[0] == nil {
			r.JSON(404, map[string]interface{}{"msg": "Question not found"})
			return
		}
		_, err = redis.Scan(r1, &pick1Id, &pick1Image, &pick2Id, &pick2Image)
		if err != nil {
			panic(err)
		}
		id1 := ""
		count1 := -1
		id2 := ""
		count2 := -1
		r2, err := redis.Values(conn.Receive())
		if err != nil {
			panic(err)
		}

		_, err = redis.Scan(r2, &id1, &count1, &id2, &count2)
		if err != nil {
			panic(err)
		}

		pick1Votes := count1
		pick2Votes := count2
		if id1 == pick2Id {
			pick1Votes = count2
			pick2Votes = count1
		}

		r.JSON(200, map[string]interface{}{
			"pick1Result": map[string]interface{}{
				"pickId":     pick1Id,
				"image":      pick1Image,
				"votesCount": pick1Votes,
			},
			"pick2Result": map[string]interface{}{
				"pickId":     pick2Id,
				"image":      pick2Image,
				"votesCount": pick2Votes,
			},
		})
	})

	m.Post("/questions/:questionId/picks/:pickId/vote", loginHandler, func(conn redis.Conn, user User, params martini.Params, r render.Render, res http.ResponseWriter) {

		pick1Id := ""
		pick2Id := ""
		questionId := params["questionId"]
		pickId := params["pickId"]
		r1, err := redis.Values(conn.Do("HMGET", fmt.Sprintf("questions:%v", questionId), "pick1Id", "pick2Id"))
		if err != nil {
			panic(err)
		}
		fmt.Println(r1)
		_, err = redis.Scan(r1, &pick1Id, &pick2Id)
		if err != nil {
			panic(err)
		}

		if pick1Id == "" || pick2Id == "" {
			r.JSON(404, map[string]interface{}{"msg": "Question not found"})
			return
		}
		if pick1Id != pickId && pick2Id != pickId {
			r.JSON(404, map[string]interface{}{"msg": "Pick not found"})
			return
		}

		userId := user.UserID
		conn.Send("WATCH", fmt.Sprintf("questionsAnswered:%v", userId))
		conn.Send("SISMEMBER", fmt.Sprintf("questionsAnswered:%v", userId), questionId)
		conn.Flush()
		_, err = conn.Receive()
		if err != nil {
			panic(err)
		}
		alreadyVoted, err := redis.Int(conn.Receive())
		if err != nil {
			panic(err)
		}
		conn.Send("MULTI")
		conn.Send("HINCRBY", fmt.Sprintf("questions:%v:votes", questionId), pickId, 1)
		conn.Send("SADD", fmt.Sprintf("questionsAnswered:%v", userId), questionId)
		if alreadyVoted == 1 {
			_, err := conn.Do("DISCARD")
			if err != nil {
				panic(err)
			}

			r.JSON(400, map[string]interface{}{"msg": "Already voted"})
			return
		}

		_, err = conn.Do("EXEC")
		if err != nil {
			panic(err)
		}
		r.JSON(200, map[string]interface{}{"msg": "Ok"})
	})

	m.Run()
}
