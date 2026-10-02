import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'

export type ConnectionState = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'ERROR' | 'NOT_CONFIGURED'
type Listener = (state:ConnectionState)=>void
class CogniVaultStompClient {
  private client:Client|null=null; private state:ConnectionState='DISCONNECTED'; private listeners=new Set<Listener>(); private subscriptions=new Map<string,StompSubscription>()
  private setState(state:ConnectionState){this.state=state;this.listeners.forEach(l=>l(state))}
  onState(listener:Listener){this.listeners.add(listener);listener(this.state);return()=>{this.listeners.delete(listener)}}
  connect(token:string){if(this.client?.active)return;const configured=import.meta.env.VITE_WS_URL;if(!configured){this.setState('NOT_CONFIGURED');return}let brokerURL=configured;if(configured.startsWith('/')){const protocol=window.location.protocol==='https:'?'wss:':'ws:';brokerURL=`${protocol}//${window.location.host}${configured}`}this.setState('CONNECTING');this.client=new Client({brokerURL,connectHeaders:{Authorization:`Bearer ${token}`},reconnectDelay:5000,heartbeatIncoming:10000,heartbeatOutgoing:10000,onConnect:()=>this.setState('CONNECTED'),onStompError:()=>this.setState('ERROR'),onWebSocketError:()=>this.setState('ERROR'),onDisconnect:()=>this.setState('DISCONNECTED')});this.client.activate()}
  subscribe(destination:string,callback:(payload:unknown,message:IMessage)=>void){if(!this.client?.connected)return()=>{};const subscription=this.client.subscribe(destination,message=>{try{callback(JSON.parse(message.body),message)}catch{callback(message.body,message)}});this.subscriptions.set(destination,subscription);return()=>{subscription.unsubscribe();this.subscriptions.delete(destination)}}
  disconnect(){this.subscriptions.forEach(s=>s.unsubscribe());this.subscriptions.clear();void this.client?.deactivate();this.client=null;this.setState('DISCONNECTED')}
}
export const stompClient=new CogniVaultStompClient()
