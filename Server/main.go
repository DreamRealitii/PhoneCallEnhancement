package main

import (
	"fmt"
	"log"
	"net/http"
	"time"

	"github.com/gorilla/websocket"
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
	CheckOrigin:     func(r *http.Request) bool { return true },
}

// Forwarder represents a WebSocket forwarder
type Forwarder struct {
	ChannelID string
	Clients   map[*websocket.Conn]bool
	Broadcast chan []byte
	Join      chan *websocket.Conn
	Leave     chan *websocket.Conn
}

// NewForwarder creates a new Forwarder
func NewForwarder(channelID string) *Forwarder {
	return &Forwarder{
		ChannelID: channelID,
		Clients:   make(map[*websocket.Conn]bool),
		Broadcast: make(chan []byte),
		Join:      make(chan *websocket.Conn),
		Leave:     make(chan *websocket.Conn),
	}
}

func (f *Forwarder) Run() {
	for {
		log.Println(time.Now().UnixMilli())
		select {
		case client := <-f.Join:
			f.Clients[client] = true
		case client := <-f.Leave:
			delete(f.Clients, client)
			//close(client.Close())
			client.Close()
		case message := <-f.Broadcast:
			log.Println("Got message", time.Now().UnixMilli())
			for client := range f.Clients {
				err := client.WriteMessage(websocket.TextMessage, message)
				if err != nil {
					fmt.Println("Error writing message to client:", err)
					f.Leave <- client
				}
				log.Println("Done sending", time.Now().UnixMilli())
			}
		}
	}
}

func wsHandler(forwarders map[string]*Forwarder, channelID string, w http.ResponseWriter, r *http.Request) {
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		fmt.Println("Error upgrading connection:", err)
		return
	}

	forwarder, ok := forwarders[channelID]
	if !ok {
		fmt.Println("Error: channel not found")
		return
	}

	forwarder.Join <- conn
	defer func() {
		forwarder.Leave <- conn
	}()

	for {
		_, message, err := conn.ReadMessage()
		if err != nil {
			fmt.Println("Error reading message:", err)
			break
		}
		forwarder.Broadcast <- message
	}
}

func main() {
	forwarders := make(map[string]*Forwarder)
	forwarders["channel-1"] = NewForwarder("channel-1")

	for _, forwarder := range forwarders {
		go forwarder.Run()
	}

	http.HandleFunc("/ws/channel-1", func(w http.ResponseWriter, r *http.Request) {
		wsHandler(forwarders, "channel-1", w, r)
	})

	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		// do NOT do this. (see below)
		http.ServeFile(w, r, r.URL.Path[1:])
	})

	log.Println("OK!")
	go http.ListenAndServe(":16385", nil)
	http.ListenAndServeTLS(":16384", "localhost.crt", "localhost.key", nil)
}
